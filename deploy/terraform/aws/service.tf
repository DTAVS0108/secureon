# SecureOne on ECS Fargate behind an Application Load Balancer (https, http redirected).

locals {
  container = "secureone"
  secrets = {
    SECUREONE_DB_PASSWORD    = aws_secretsmanager_secret.db_password.arn
    SECUREONE_REDIS_URL      = aws_secretsmanager_secret.redis_url.arn
    SECUREONE_ENCRYPTION_KEY = aws_secretsmanager_secret.encryption_key.arn
  }
  environment = {
    SECUREONE_PUBLIC_BASE_URL = var.public_base_url
    SECUREONE_DB_URL          = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/secureone?sslmode=require&currentSchema=platform"
    SECUREONE_DB_USERNAME     = aws_db_instance.main.username
    # The load balancer's addresses: it may tell SecureOne the visitor's own address.
    SECUREONE_TRUSTED_PROXIES = var.vpc_cidr
  }
}

resource "aws_cloudwatch_log_group" "main" {
  name              = "/ecs/${var.name}"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_cluster" "main" {
  name = var.name
}

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.name}-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = values(local.secrets)
  }
}

resource "aws_iam_role_policy" "read_secrets" {
  name   = "read-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.read_secrets.json
}

resource "aws_ecs_task_definition" "main" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  container_definitions = jsonencode([{
    name         = local.container
    image        = var.image
    essential    = true
    portMappings = [{ containerPort = 9000, protocol = "tcp" }]
    environment  = [for k, v in local.environment : { name = k, value = v }]
    secrets      = [for k, v in local.secrets : { name = k, valueFrom = v }]
    stopTimeout  = 60
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.main.name
        awslogs-region        = var.region
        awslogs-stream-prefix = local.container
      }
    }
  }])
  # The secret values must exist before the first task starts.
  depends_on = [
    aws_secretsmanager_secret_version.db_password,
    aws_secretsmanager_secret_version.redis_url,
    aws_secretsmanager_secret_version.encryption_key,
  ]
}

resource "aws_lb" "main" {
  name                       = var.name
  load_balancer_type         = "application"
  subnets                    = aws_subnet.public[*].id
  security_groups            = [aws_security_group.alb.id]
  drop_invalid_header_fields = true
  enable_deletion_protection = var.deletion_protection
}

resource "aws_lb_target_group" "main" {
  name                 = var.name
  port                 = 9000
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = aws_vpc.main.id
  deregistration_delay = 30
  health_check {
    path                = "/api/info"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 4
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.main.arn
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_ecs_service" "main" {
  name                              = var.name
  cluster                           = aws_ecs_cluster.main.id
  task_definition                   = aws_ecs_task_definition.main.arn
  desired_count                     = var.desired_count
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 240
  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.main.arn
    container_name   = local.container
    container_port   = 9000
  }
  depends_on = [aws_lb_listener.https]
}

{{/* The image every SecureOne container runs. */}}
{{- define "secureone.image" -}}
"{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
{{- end }}

{{/* Secrets and settings shared by the pods and the migration job. */}}
{{- define "secureone.env" -}}
envFrom:
  - secretRef:
      name: {{ .Values.existingSecret }}
env:
  {{- range $key, $value := .Values.env }}
  - name: {{ $key }}
    value: {{ $value | quote }}
  {{- end }}
{{- end }}

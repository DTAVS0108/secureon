{{/* The image every SecureOne container runs. */}}
{{- define "secureone.image" -}}
"{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
{{- end }}

{{/* The Secret the pods read: yours, or the one the chart creates (secret.yaml). */}}
{{- define "secureone.secretName" -}}
{{- .Values.existingSecret | default (printf "%s-secrets" .Release.Name) -}}
{{- end }}

{{/*
Secrets and settings shared by the pods and the migration job. Kubernetes lets env win over envFrom, so the
settings below override the same names in the Secret; each is only set when given.
*/}}
{{- define "secureone.env" -}}
envFrom:
  - secretRef:
      name: {{ include "secureone.secretName" . }}
env:
  - name: SECUREONE_PUBLIC_BASE_URL
    value: {{ required "publicUrl is required (https://...)" .Values.publicUrl | quote }}
  {{- with .Values.database.url }}
  - name: SECUREONE_DB_URL
    value: {{ . | quote }}
  {{- end }}
  {{- with .Values.database.username }}
  - name: SECUREONE_DB_USERNAME
    value: {{ . | quote }}
  {{- end }}
  {{- with .Values.redis.url }}
  - name: SECUREONE_REDIS_URL
    value: {{ . | quote }}
  {{- end }}
  {{- range $key, $value := .Values.env }}
  - name: {{ $key }}
    value: {{ $value | quote }}
  {{- end }}
{{- end }}

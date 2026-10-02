{{- define "ultimate-analytics.fullname" -}}
{{- if contains .Chart.Name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "ultimate-analytics.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "ultimate-analytics.labels" -}}
{{ include "ultimate-analytics.selectorLabels" . }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "ultimate-analytics.image" -}}
{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}
{{- end -}}

{{- define "ultimate-analytics.secretName" -}}
{{- .Values.database.existingSecret | default (printf "%s-db" (include "ultimate-analytics.fullname" .)) -}}
{{- end -}}

{{/* Database settings shared by the app and the migration Job. */}}
{{- define "ultimate-analytics.databaseEnv" -}}
- name: SPRING_DATASOURCE_URL
  value: {{ required "database.url is required" .Values.database.url | quote }}
- name: SPRING_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "ultimate-analytics.secretName" . }}
      key: {{ .Values.database.usernameKey }}
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "ultimate-analytics.secretName" . }}
      key: {{ .Values.database.passwordKey }}
{{- end -}}

{{- define "ultimate-analytics.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end -}}

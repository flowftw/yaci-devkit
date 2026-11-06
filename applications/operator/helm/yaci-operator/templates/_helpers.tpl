{{/*
Expand the name of the chart.
*/}}
{{- define "yaci-operator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "yaci-operator.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "yaci-operator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "yaci-operator.labels" -}}
helm.sh/chart: {{ include "yaci-operator.chart" . }}
{{ include "yaci-operator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "yaci-operator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "yaci-operator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "yaci-operator.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "yaci-operator.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
Return the target namespaces for the operator.
If clusterWide is true, returns empty (watch all).
If targetNamespaces is set, returns them comma-separated.
Otherwise, defaults to the release namespace.
*/}}
{{- define "yaci-operator.targetNamespaces" -}}
{{- if not .Values.rbac.clusterWide -}}
{{- if .Values.rbac.targetNamespaces -}}
{{- join "," .Values.rbac.targetNamespaces -}}
{{- else -}}
{{- .Release.Namespace -}}
{{- end -}}
{{- end -}}
{{- end -}}

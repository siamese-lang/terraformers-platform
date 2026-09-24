variable "project_id" {
  description = "GCP project that owns the single Terraformers target runtime."
  type        = string
}

variable "region" {
  description = "Target GCP region."
  type        = string
  default     = "asia-northeast3"
}

variable "zone" {
  description = "Initial zonal GKE Standard location. Refresh capacity before apply."
  type        = string
  default     = "asia-northeast3-a"
}

variable "cluster_name" {
  description = "Single reusable target GKE cluster name."
  type        = string
  default     = "terraformers-target"
}

variable "network_name" {
  description = "Dedicated target runtime VPC name."
  type        = string
  default     = "terraformers-target"
}

variable "subnet_name" {
  description = "Dedicated target runtime subnet name."
  type        = string
  default     = "terraformers-target"
}

variable "subnet_cidr" {
  description = "Target runtime subnet CIDR. Confirm no project conflict before apply."
  type        = string
}

variable "node_machine_type" {
  description = "Initial GKE node machine type; sizing remains a deployment variable."
  type        = string
  default     = "e2-standard-2"
}

variable "node_count" {
  description = "Target node count. Keep 0 while idle; set 1 only for approved live verification sessions."
  type        = number
  default     = 0

  validation {
    condition     = var.node_count >= 0
    error_message = "node_count must be non-negative."
  }
}

variable "node_disk_type" {
  description = "GKE node boot disk type."
  type        = string
  default     = "pd-standard"
}

variable "node_disk_size_gb" {
  description = "GKE node boot disk size."
  type        = number
  default     = 30
}

variable "backend_namespace" {
  description = "Namespace that owns the backend Kubernetes ServiceAccount."
  type        = string
  default     = "terraformers-target"
}

variable "backend_service_account" {
  description = "Kubernetes ServiceAccount used by the backend."
  type        = string
  default     = "terraformers-backend"
}

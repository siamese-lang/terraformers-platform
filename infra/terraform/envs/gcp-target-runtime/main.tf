locals {
  required_services = toset([
    "aiplatform.googleapis.com",
    "compute.googleapis.com",
    "container.googleapis.com",
    "iamcredentials.googleapis.com",
  ])

  backend_workload_principal = "principal://iam.googleapis.com/projects/${data.google_project.current.number}/locations/global/workloadIdentityPools/${var.project_id}.svc.id.goog/subject/ns/${var.backend_namespace}/sa/${var.backend_service_account}"

  node_roles = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
  ])
}

data "google_project" "current" {
  project_id = var.project_id
}

resource "google_project_service" "required" {
  for_each = local.required_services

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_compute_network" "target" {
  name                    = var.network_name
  auto_create_subnetworks = false

  depends_on = [google_project_service.required["compute.googleapis.com"]]
}

resource "google_compute_subnetwork" "target" {
  name          = var.subnet_name
  ip_cidr_range = var.subnet_cidr
  region        = var.region
  network       = google_compute_network.target.id
}

resource "google_service_account" "gke_nodes" {
  account_id   = "terraformers-gke-nodes"
  display_name = "Terraformers target GKE nodes"
}

resource "google_project_iam_member" "gke_node_roles" {
  for_each = local.node_roles

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.gke_nodes.email}"
}

resource "google_container_cluster" "target" {
  name     = var.cluster_name
  location = var.zone

  network    = google_compute_network.target.id
  subnetwork = google_compute_subnetwork.target.id

  remove_default_node_pool = true
  initial_node_count       = 1
  deletion_protection      = false

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  addons_config {
    gce_persistent_disk_csi_driver_config {
      enabled = true
    }
  }

  ip_allocation_policy {}

  release_channel {
    channel = "REGULAR"
  }

  depends_on = [google_project_service.required["container.googleapis.com"]]
}

resource "google_container_node_pool" "target" {
  name     = "${var.cluster_name}-primary"
  cluster  = google_container_cluster.target.name
  location = var.zone

  node_count = var.node_count

  node_config {
    machine_type    = var.node_machine_type
    disk_type       = var.node_disk_type
    disk_size_gb    = var.node_disk_size_gb
    service_account = google_service_account.gke_nodes.email
    oauth_scopes    = ["https://www.googleapis.com/auth/cloud-platform"]

    workload_metadata_config {
      mode = "GKE_METADATA"
    }

    linux_node_config {
      sysctls = {
        "vm.max_map_count" = "262144"
      }
    }

    labels = {
      "terraformers-runtime" = "target"
    }
  }

  depends_on = [google_project_iam_member.gke_node_roles]
}

resource "google_project_iam_member" "backend_vertex" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = local.backend_workload_principal

  depends_on = [
    google_container_cluster.target,
    google_project_service.required["aiplatform.googleapis.com"],
  ]
}

resource "google_project_iam_member" "backend_service_usage" {
  project = var.project_id
  role    = "roles/serviceusage.serviceUsageConsumer"
  member  = local.backend_workload_principal

  depends_on = [google_container_cluster.target]
}

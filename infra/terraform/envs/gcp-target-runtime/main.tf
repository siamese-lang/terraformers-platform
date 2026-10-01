locals {
  required_services = toset([
    "aiplatform.googleapis.com",
    "compute.googleapis.com",
    "container.googleapis.com",
    "iamcredentials.googleapis.com",
  ])

  backend_workload_principal     = "principal://iam.googleapis.com/projects/${data.google_project.current.number}/locations/global/workloadIdentityPools/${var.project_id}.svc.id.goog/subject/ns/${var.backend_namespace}/sa/${var.backend_service_account}"
  secret_sync_workload_principal = "principal://iam.googleapis.com/projects/${data.google_project.current.number}/locations/global/workloadIdentityPools/${var.project_id}.svc.id.goog/subject/ns/${var.backend_namespace}/sa/terraformers-secret-sync"

  node_roles = toset([
    "roles/container.defaultNodeServiceAccount",
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

  # GKE must create a temporary default node pool before removing it. Use the
  # same least-privilege custom node identity so cluster creation never depends
  # on permissions of the Compute Engine default service account.
  node_config {
    service_account = google_service_account.gke_nodes.email
    oauth_scopes    = ["https://www.googleapis.com/auth/cloud-platform"]
  }

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  addons_config {
    gce_persistent_disk_csi_driver_config {
      enabled = true
    }
  }

  dynamic "secret_sync_config" {
    for_each = var.enable_runtime_dependencies ? [1] : []
    content {
      enabled = true
    }
  }

  ip_allocation_policy {}

  release_channel {
    channel = "REGULAR"
  }

  depends_on = [
    google_project_service.required["container.googleapis.com"],
    google_project_iam_member.gke_node_roles,
  ]
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


# Case C immutable backend image delivery foundation. The default is enabled so
# the canonical desired state retains the resources after first delivery apply. Historical
# foundation operations explicitly set this false to preserve their reviewed 12-resource contract.
variable "enable_delivery_foundation" {
  description = "Include the immutable backend image delivery foundation in canonical target state."
  type        = bool
  default     = true
}

resource "google_project_service" "artifact_registry" {
  count = var.enable_delivery_foundation ? 1 : 0

  project            = var.project_id
  service            = "artifactregistry.googleapis.com"
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "backend" {
  count = var.enable_delivery_foundation ? 1 : 0

  project       = var.project_id
  location      = var.region
  repository_id = "terraformers-backend"
  description   = "Immutable Terraformers backend images"
  format        = "DOCKER"

  depends_on = [google_project_service.artifact_registry[0]]
}

resource "google_artifact_registry_repository_iam_member" "publisher_writer" {
  count = var.enable_delivery_foundation ? 1 : 0

  project    = var.project_id
  location   = google_artifact_registry_repository.backend[0].location
  repository = google_artifact_registry_repository.backend[0].repository_id
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:terraformers-image-publish@${var.project_id}.iam.gserviceaccount.com"
}

resource "google_artifact_registry_repository_iam_member" "gke_node_reader" {
  count = var.enable_delivery_foundation ? 1 : 0

  project    = var.project_id
  location   = google_artifact_registry_repository.backend[0].location
  repository = google_artifact_registry_repository.backend[0].repository_id
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.gke_nodes.email}"
}

resource "google_artifact_registry_repository_iam_member" "plan_reader" {
  count = var.enable_delivery_foundation ? 1 : 0

  project    = var.project_id
  location   = google_artifact_registry_repository.backend[0].location
  repository = google_artifact_registry_repository.backend[0].repository_id
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:terraformers-plan@${var.project_id}.iam.gserviceaccount.com"
}


# Case C runtime dependency foundation. Secret containers are created in a
# separate first apply so secret payloads never need to enter Terraform state.
resource "google_project_service" "secret_manager" {
  count = var.enable_runtime_secret_foundation ? 1 : 0

  project            = var.project_id
  service            = "secretmanager.googleapis.com"
  disable_on_destroy = false
}

resource "google_secret_manager_secret" "mariadb_root" {
  count = var.enable_runtime_secret_foundation ? 1 : 0

  project   = var.project_id
  secret_id = "terraformers-mariadb-root-password"

  replication {
    auto {}
  }

  depends_on = [google_project_service.secret_manager[0]]
}

resource "google_secret_manager_secret" "mariadb_app" {
  count = var.enable_runtime_secret_foundation ? 1 : 0

  project   = var.project_id
  secret_id = "terraformers-mariadb-app-password"

  replication {
    auto {}
  }

  depends_on = [google_project_service.secret_manager[0]]
}

resource "google_storage_bucket" "runtime_objects" {
  count = var.enable_runtime_dependencies ? 1 : 0

  name                        = "terraformers-runtime-objects-${data.google_project.current.number}"
  project                     = var.project_id
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = false
}

resource "google_storage_bucket_iam_member" "backend_object_user" {
  count = var.enable_runtime_dependencies ? 1 : 0

  bucket = google_storage_bucket.runtime_objects[0].name
  role   = "roles/storage.objectUser"
  member = local.backend_workload_principal
}

resource "google_service_account" "mariadb" {
  count = var.enable_runtime_dependencies ? 1 : 0

  account_id   = "terraformers-mariadb"
  display_name = "Terraformers MariaDB runtime"
}

resource "google_secret_manager_secret_iam_member" "mariadb_root_accessor" {
  count = var.enable_runtime_dependencies ? 1 : 0

  project   = var.project_id
  secret_id = google_secret_manager_secret.mariadb_root[0].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.mariadb[0].email}"
}

resource "google_secret_manager_secret_iam_member" "mariadb_app_accessor" {
  count = var.enable_runtime_dependencies ? 1 : 0

  project   = var.project_id
  secret_id = google_secret_manager_secret.mariadb_app[0].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.mariadb[0].email}"
}

resource "google_secret_manager_secret_iam_member" "secret_sync_app_accessor" {
  count = var.enable_runtime_dependencies ? 1 : 0

  project   = var.project_id
  secret_id = google_secret_manager_secret.mariadb_app[0].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = local.secret_sync_workload_principal
}

resource "google_compute_disk" "mariadb_data" {
  count = var.enable_runtime_dependencies ? 1 : 0

  name = "terraformers-mariadb-data"
  type = "pd-balanced"
  zone = var.zone
  size = 20
}

resource "google_compute_instance" "mariadb" {
  count = var.enable_runtime_dependencies ? 1 : 0

  name                      = "terraformers-mariadb"
  machine_type              = "e2-medium"
  zone                      = var.zone
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      image = "cos-cloud/cos-stable"
      size  = 10
      type  = "pd-balanced"
    }
  }

  attached_disk {
    source      = google_compute_disk.mariadb_data[0].id
    device_name = "terraformers-mariadb-data"
  }

  network_interface {
    subnetwork = google_compute_subnetwork.target.id

    # Ephemeral IPv4 is outbound bootstrap only. No public ingress firewall rule exists.
    access_config {}
  }

  service_account {
    email  = google_service_account.mariadb[0].email
    scopes = ["https://www.googleapis.com/auth/cloud-platform"]
  }

  metadata_startup_script = <<-EOT
    #!/bin/bash
    set -euo pipefail

    PROJECT_ID="terraformers-platform"
    DATA_DEVICE="/dev/disk/by-id/google-terraformers-mariadb-data"
    DATA_MOUNT="/mnt/disks/terraformers-mariadb"
    MARIADB_IMAGE="${var.mariadb_image}"

    [[ "$MARIADB_IMAGE" =~ ^mariadb:11\.4@sha256:[0-9a-f]{64}$ ]] || {
      echo "MariaDB image must be an exact 11.4 digest reference." >&2
      exit 1
    }

    mkdir -p "$DATA_MOUNT"
    if ! blkid "$DATA_DEVICE" >/dev/null 2>&1; then
      mkfs.ext4 -m 0 -F "$DATA_DEVICE"
    fi
    mountpoint -q "$DATA_MOUNT" || mount -o discard,defaults "$DATA_DEVICE" "$DATA_MOUNT"

    access_secret() {
      local secret_name="$1"
      local token payload
      token="$(
        curl -fsS -H 'Metadata-Flavor: Google'           'http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token'           | sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
      )"
      [[ -n "$token" ]]
      payload="$(
        curl -fsS           -H "Authorization: Bearer $${token}"           "https://secretmanager.googleapis.com/v1/projects/$${PROJECT_ID}/secrets/$${secret_name}/versions/latest:access"           | sed -n 's/.*"data"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
      )"
      [[ -n "$payload" ]]
      printf '%s' "$payload" | base64 -d
    }

    ROOT_PASSWORD="$(access_secret terraformers-mariadb-root-password)"
    APP_PASSWORD="$(access_secret terraformers-mariadb-app-password)"
    [[ -n "$ROOT_PASSWORD" && -n "$APP_PASSWORD" ]]

    docker pull "$MARIADB_IMAGE"
    docker rm -f terraformers-mariadb >/dev/null 2>&1 || true
    docker run -d       --name terraformers-mariadb       --restart unless-stopped       --network host       -e MARIADB_ROOT_PASSWORD="$ROOT_PASSWORD"       -e MARIADB_DATABASE=terraformers       -e MARIADB_USER=terraformers       -e MARIADB_PASSWORD="$APP_PASSWORD"       -v "$DATA_MOUNT:/var/lib/mysql"       "$MARIADB_IMAGE"

    unset ROOT_PASSWORD APP_PASSWORD
  EOT

  depends_on = [
    google_secret_manager_secret_iam_member.mariadb_root_accessor,
    google_secret_manager_secret_iam_member.mariadb_app_accessor,
  ]
}

resource "google_compute_firewall" "mariadb_from_gke" {
  count = var.enable_runtime_dependencies ? 1 : 0

  name        = "terraformers-mariadb-from-gke"
  network     = google_compute_network.target.name
  direction   = "INGRESS"
  description = "Allow MariaDB only from the target GKE subnet and Pod address range."

  allow {
    protocol = "tcp"
    ports    = ["3306"]
  }

  source_ranges = distinct([
    var.subnet_cidr,
    google_container_cluster.target.ip_allocation_policy[0].cluster_ipv4_cidr_block,
  ])

  target_service_accounts = [google_service_account.mariadb[0].email]
}

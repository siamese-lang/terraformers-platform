output "project_id" {
  value = var.project_id
}

output "region" {
  value = var.region
}

output "zone" {
  value = var.zone
}

output "cluster_name" {
  value = google_container_cluster.target.name
}

output "network_name" {
  value = google_compute_network.target.name
}

output "subnet_name" {
  value = google_compute_subnetwork.target.name
}

output "backend_workload_principal" {
  value = local.backend_workload_principal
}


output "backend_artifact_repository" {
  value = google_artifact_registry_repository.backend.name
}

output "backend_image_base" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.backend.repository_id}/terraformers-backend"
}

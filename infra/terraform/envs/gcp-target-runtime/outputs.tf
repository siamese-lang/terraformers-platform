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
  value = var.enable_delivery_foundation ? google_artifact_registry_repository.backend[0].name : null
}

output "backend_image_base" {
  value = var.enable_delivery_foundation ? "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.backend[0].repository_id}/terraformers-backend" : null
}

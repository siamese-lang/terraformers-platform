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


output "runtime_object_bucket_name" {
  value = var.enable_runtime_dependencies ? google_storage_bucket.runtime_objects[0].name : null
}

output "mariadb_instance_name" {
  value = var.enable_runtime_dependencies ? google_compute_instance.mariadb[0].name : null
}

output "mariadb_private_ip" {
  value = var.enable_runtime_dependencies ? google_compute_instance.mariadb[0].network_interface[0].network_ip : null
}

output "mariadb_root_secret_id" {
  value = var.enable_runtime_secret_foundation ? google_secret_manager_secret.mariadb_root[0].secret_id : null
}

output "mariadb_app_secret_id" {
  value = var.enable_runtime_secret_foundation ? google_secret_manager_secret.mariadb_app[0].secret_id : null
}

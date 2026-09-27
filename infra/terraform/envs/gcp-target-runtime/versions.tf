terraform {
  required_version = ">= 1.15.0"

  # The bucket is created once outside this runtime root; supply its name at init.
  backend "gcs" {}

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "8.3.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

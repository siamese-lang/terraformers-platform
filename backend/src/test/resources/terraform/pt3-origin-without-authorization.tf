resource "aws_s3_bucket" "delivery_store" {
  bucket = var.bucket_name
}

resource "aws_cloudfront_origin_access_control" "origin_signer" {
  name                              = var.control_name
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "edge" {
  origin {
    domain_name              = aws_s3_bucket.delivery_store.bucket_regional_domain_name
    origin_id                = "static-files"
    origin_access_control_id = aws_cloudfront_origin_access_control.origin_signer.id
  }
}

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.100.0"
    }
  }
}

variable "domain_name" {
  type        = string
  description = "Primary domain name managed in Route 53 and ACM"
}

variable "origin_domain_name" {
  type        = string
  description = "Domain name of the backend custom origin for CloudFront"
}

variable "lambda_artifact_path" {
  type        = string
  description = "Path to the deployment package zip file for Lambda functions"
}

resource "aws_route53_zone" "primary" {
  name = var.domain_name
}

resource "aws_acm_certificate" "cert" {
  domain_name       = var.domain_name
  validation_method = "DNS"
  subject_alternative_names = [
    "*.${var.domain_name}"
  ]
}

resource "aws_cloudfront_distribution" "app_distribution" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"

  origin {
    domain_name = var.origin_domain_name
    origin_id   = "web-origin"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD", "OPTIONS"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "web-origin"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    viewer_protocol_policy = "redirect-to-https"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate.cert.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

resource "aws_cognito_user_pool" "pool" {
  name = "app-user-pool"
}

resource "aws_dynamodb_table" "app_table" {
  name         = "app-data-table"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }
}

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "dynamodb_access" {
  statement {
    actions = [
      "dynamodb:GetItem",
      "dynamodb:Query",
      "dynamodb:Scan",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:DeleteItem"
    ]
    resources = [aws_dynamodb_table.app_table.arn]
  }
}

resource "aws_iam_role" "lambda_ticket_role" {
  name               = "lambda-ticket-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json

  inline_policy {
    name   = "dynamodb-access"
    policy = data.aws_iam_policy_document.dynamodb_access.json
  }
}

resource "aws_iam_role" "lambda_show_role" {
  name               = "lambda-show-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json

  inline_policy {
    name   = "dynamodb-access"
    policy = data.aws_iam_policy_document.dynamodb_access.json
  }
}

resource "aws_iam_role" "lambda_info_role" {
  name               = "lambda-info-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json

  inline_policy {
    name   = "dynamodb-access"
    policy = data.aws_iam_policy_document.dynamodb_access.json
  }
}

resource "aws_lambda_function" "tickets" {
  function_name = "tickets-function"
  role          = aws_iam_role.lambda_ticket_role.arn
  handler       = "index.handler"
  runtime       = "nodejs20.x"
  filename      = var.lambda_artifact_path
}

resource "aws_lambda_function" "shows" {
  function_name = "shows-function"
  role          = aws_iam_role.lambda_show_role.arn
  handler       = "index.handler"
  runtime       = "nodejs20.x"
  filename      = var.lambda_artifact_path
}

resource "aws_lambda_function" "info" {
  function_name = "info-function"
  role          = aws_iam_role.lambda_info_role.arn
  handler       = "index.handler"
  runtime       = "nodejs20.x"
  filename      = var.lambda_artifact_path
}

resource "aws_api_gateway_rest_api" "main" {
  name = "serverless-app-api"
}

resource "aws_api_gateway_authorizer" "cognito" {
  name          = "cognito-authorizer"
  rest_api_id   = aws_api_gateway_rest_api.main.id
  type          = "COGNITO_USER_POOLS"
  provider_arns = [aws_cognito_user_pool.pool.arn]
}

resource "aws_api_gateway_resource" "tickets" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_rest_api.main.root_resource_id
  path_part   = "tickets"
}

resource "aws_api_gateway_method" "tickets_get" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  resource_id   = aws_api_gateway_resource.tickets.id
  http_method   = "GET"
  authorization = "COGNITO_USER_POOLS"
  authorizer_id = aws_api_gateway_authorizer.cognito.id
}

resource "aws_api_gateway_integration" "tickets_integration" {
  rest_api_id             = aws_api_gateway_rest_api.main.id
  resource_id             = aws_api_gateway_resource.tickets.id
  http_method             = aws_api_gateway_method.tickets_get.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.tickets.invoke_arn
}

resource "aws_api_gateway_resource" "shows" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_rest_api.main.root_resource_id
  path_part   = "shows"
}

resource "aws_api_gateway_method" "shows_get" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  resource_id   = aws_api_gateway_resource.shows.id
  http_method   = "GET"
  authorization = "COGNITO_USER_POOLS"
  authorizer_id = aws_api_gateway_authorizer.cognito.id
}

resource "aws_api_gateway_integration" "shows_integration" {
  rest_api_id             = aws_api_gateway_rest_api.main.id
  resource_id             = aws_api_gateway_resource.shows.id
  http_method             = aws_api_gateway_method.shows_get.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.shows.invoke_arn
}

resource "aws_api_gateway_resource" "info" {
  rest_api_id = aws_api_gateway_rest_api.main.id
  parent_id   = aws_api_gateway_rest_api.main.root_resource_id
  path_part   = "info"
}

resource "aws_api_gateway_method" "info_get" {
  rest_api_id   = aws_api_gateway_rest_api.main.id
  resource_id   = aws_api_gateway_resource.info.id
  http_method   = "GET"
  authorization = "COGNITO_USER_POOLS"
  authorizer_id = aws_api_gateway_authorizer.cognito.id
}

resource "aws_api_gateway_integration" "info_integration" {
  rest_api_id             = aws_api_gateway_rest_api.main.id
  resource_id             = aws_api_gateway_resource.info.id
  http_method             = aws_api_gateway_method.info_get.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.info.invoke_arn
}

resource "aws_api_gateway_deployment" "main" {
  rest_api_id = aws_api_gateway_rest_api.main.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_resource.tickets.id,
      aws_api_gateway_method.tickets_get.id,
      aws_api_gateway_integration.tickets_integration.id,
      aws_api_gateway_resource.shows.id,
      aws_api_gateway_method.shows_get.id,
      aws_api_gateway_integration.shows_integration.id,
      aws_api_gateway_resource.info.id,
      aws_api_gateway_method.info_get.id,
      aws_api_gateway_integration.info_integration.id,
      aws_api_gateway_authorizer.cognito.id
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "prod" {
  deployment_id = aws_api_gateway_deployment.main.id
  rest_api_id   = aws_api_gateway_rest_api.main.id
  stage_name    = "prod"
}

resource "aws_lambda_permission" "apigw_tickets" {
  statement_id  = "AllowAPIGatewayInvokeTickets"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.tickets.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.main.execution_arn}/*/*"
}

resource "aws_lambda_permission" "apigw_shows" {
  statement_id  = "AllowAPIGatewayInvokeShows"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.shows.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.main.execution_arn}/*/*"
}

resource "aws_lambda_permission" "apigw_info" {
  statement_id  = "AllowAPIGatewayInvokeInfo"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.info.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.main.execution_arn}/*/*"
}
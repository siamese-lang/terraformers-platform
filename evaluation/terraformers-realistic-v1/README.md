# Terraformers realistic-input candidate v1

**CANDIDATE — NOT TRUTH-FROZEN — NO MODEL RESULTS**

PT-1 prepares a reviewable benchmark before any realistic live baseline. It contains six independently redrawn public-reference AWS patterns, two explicitly incomplete design controls, and two non-architecture controls. No user-owned real diagrams were discoverable in the repository at the bound main SHA. These are reference-based recreations, not collected user uploads; they cannot alone establish realistic-image generalization.

The existing `m3-evaluation-v1` schema and `EvaluationDatasetLoader` load `dataset.json` without production changes. `provenance.json` holds provenance, semantic resource intent, variants and rejection reasons that the frozen schema cannot express. It is a human-review supplement, not a new evaluator. The existing exact-string/resource-type evaluator does not prove cardinality, full boundary correctness or semantic equivalence; PT-2 must retain the frozen human truth as the authority instead of treating its aggregate score as fidelity acceptance.

## Identity and reuse

- PNG bytes are the authoritative future inference inputs. SVG files are editable, repository-owned topology representations; they are not model inputs.
- Every PNG SHA-256 is pinned in `dataset.json`; every SVG SHA-256 is pinned in `provenance.json`.
- PNG rendering used Pillow 12.3.0 and DejaVu Sans. Re-rendering SVG may produce different pixels and requires a new checksum plus renewed review; use committed PNG bytes for comparisons.
- `candidate-identity.json` pins the dataset, provenance, README, and all 20 fixture files. Its digest is recorded in the PT-1 review evidence; it is a candidate identity, not human approval.
- Synthetic canonical and holdout datasets remain unchanged. No model calls, output-driven fixture changes, scoring changes or cloud actions were performed.

## Provenance and redistribution boundary

All shapes, layouts, annotations and control documents are original PT-1 contributions. AWS service names are factual labels; no AWS icons, diagrams, screenshots or copied prose are bundled. Public documentation supplies topology facts, not permission to copy artwork. Source document URLs, resolved URLs, access date and observed HTML SHA-256 are recorded in `provenance.json`. The HTML snapshots are external investigation evidence, not redistributed repository assets. Website bytes may subsequently change; the committed topology and PNG bytes remain pinned.

Source descriptions below are paraphrases. Explicit selections (HTTP API, IPv4-only, two buckets, crawler/workgroup) are visible in the images and recorded as adaptations, rather than silently attributed to source defaults.

## Review scope

The agent drafted labels from the inspected specifications and explicit drawing annotations. Terraformers/Vertex/Gemini was not run and did not author or validate its own truth. Human review must confirm every image, component, relationship, resource intent, forbidden interpretation and provenance basis. No unresolved factual truth guess is retained; intentional incomplete-design cases have explicit rejection truth. Every case is still unapproved.

All positives use service labels instead of third-party icons. The set covers landscape/portrait, two-lane/fanout/parallel/crossing-line layouts, nested subnet/AZ boundaries and dense small annotations, but it does not test arbitrary icon recognition, camera noise, real screenshot artifacts, or multilingual diagrams.

The `acceptable` resource lists allow support resources; semantic descriptions below do not amend scorer behavior. `validation: PASS` is an expected positive outcome, not a recorded execution result.

## pt1-01-serverless-portal

![pt1-01-serverless-portal](fixtures/pt1-01-serverless-portal.png)

Classification: `ARCHITECTURE_DIAGRAM`. Layout: Two lanes; bent ingress lines; separate storage families.

Source basis: AWS architecture blog describes S3/CloudFront presentation, API Gateway/Lambda business logic and DynamoDB data. Authentication omitted because no identity service is specified here.

- [Building a three-tier architecture on a budget | AWS Architecture Blog](https://aws.amazon.com/blogs/architecture/building-a-three-tier-architecture-on-a-budget/)

Required components: Browser users; Amazon CloudFront; Amazon S3 website assets; Amazon API Gateway; AWS Lambda business logic; Amazon DynamoDB application table.

Required relationships:

- Browser users -> Amazon CloudFront
- Amazon CloudFront -> Amazon S3 website assets
- Browser users -> Amazon API Gateway
- Amazon API Gateway -> AWS Lambda business logic
- AWS Lambda business logic -> Amazon DynamoDB application table

Required resource types: `aws_cloudfront_distribution`, `aws_s3_bucket`, `aws_apigatewayv2_api`, `aws_lambda_function`, `aws_dynamodb_table`.

Required semantic intent:

- Separate static S3 origin and HTTP API paths; Lambda owns business logic and DynamoDB stores application records.

Acceptable variants: Service-name synonyms are acceptable; support resources are allowed. HTTP API annotation binds the v2 API resource, not a REST API swap.

Allowed support types: `aws_apigatewayv2_integration`, `aws_apigatewayv2_route`, `aws_apigatewayv2_stage`, `aws_lambda_permission`, `aws_iam_role`, `aws_iam_policy`, `aws_cloudfront_origin_access_control`.

Forbidden components: Application Load Balancer; RDS Database; EKS.
Forbidden relationships: Amazon CloudFront -> Application Load Balancer.
Forbidden resource types: `aws_lb`, `aws_db_instance`, `aws_eks_cluster`.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-02-order-fanout

![pt1-02-order-fanout](fixtures/pt1-02-order-fanout.png)

Classification: `ARCHITECTURE_DIAGRAM`. Layout: Mirrored worker lanes; orthogonal split; small batch annotations.

Source basis: Composition of documented SNS-to-SQS fanout and Lambda SQS event-source mapping. Billing/audit names are original concrete roles; no storage destination is invented.

- [Fanout Amazon SNS notifications to Amazon SQS queues for asynchronous processing - Amazon Simple Notification Service](https://docs.aws.amazon.com/sns/latest/dg/sns-sqs-as-subscriber.html)
- [Using Lambda with Amazon SQS - AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/with-sqs.html)

Required components: Order publisher; Amazon SNS orders topic; Amazon SQS billing queue; Amazon SQS audit queue; AWS Lambda billing worker; AWS Lambda audit worker.

Required relationships:

- Order publisher -> Amazon SNS orders topic
- Amazon SNS orders topic -> Amazon SQS billing queue
- Amazon SNS orders topic -> Amazon SQS audit queue
- Amazon SQS billing queue -> AWS Lambda billing worker
- Amazon SQS audit queue -> AWS Lambda audit worker

Required resource types: `aws_sns_topic`, `aws_sns_topic_subscription`, `aws_sqs_queue`, `aws_sqs_queue_policy`, `aws_lambda_function`, `aws_lambda_event_source_mapping`.

Required semantic intent:

- One SNS topic fans out to two distinct SQS queues. Each queue feeds its own Lambda consumer using an event-source mapping; queue policy allows SNS delivery.

Acceptable variants: Relationship arrows denote event/data flow; Lambda polls SQS rather than SQS invoking Lambda directly.

Allowed support types: `aws_iam_role`, `aws_iam_policy`.

Forbidden components: Amazon API Gateway; RDS Database.
Forbidden relationships: Amazon SNS orders topic -> AWS Lambda billing worker; Amazon SNS orders topic -> AWS Lambda audit worker.
Forbidden resource types: `aws_db_instance`.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-03-parallel-lookup

![pt1-03-parallel-lookup](fixtures/pt1-03-parallel-lookup.png)

Classification: `ARCHITECTURE_DIAGRAM`. Layout: Vertical workflow; two parallel branches and explicit join; no VPC boxes.

Source basis: Independent redraw of the documented LookupCustomerInfo parallel example with AddressFinder and PhoneFinder Lambda tasks, preserving both terminal branches.

- [Parallel workflow state - AWS Step Functions](https://docs.aws.amazon.com/step-functions/latest/dg/state-parallel.html)

Required components: AWS Step Functions state machine; Parallel LookupCustomerInfo; AWS Lambda AddressFinder; AWS Lambda PhoneFinder.

Required relationships:

- Parallel LookupCustomerInfo -> AWS Lambda AddressFinder
- Parallel LookupCustomerInfo -> AWS Lambda PhoneFinder
- AWS Lambda AddressFinder -> End / result array
- AWS Lambda PhoneFinder -> End / result array

Required resource types: `aws_sfn_state_machine`, `aws_lambda_function`, `aws_iam_role`.

Required semantic intent:

- A Step Functions Parallel state invokes two distinct Lambda tasks and joins their outputs. Workflow states are not separately deployed cloud services.

Acceptable variants: Parallel/join can be described in words rather than as separate components; terminal result array is logical output, not a database.

Allowed support types: `aws_iam_policy`, `aws_iam_role_policy`.

Forbidden components: Amazon SQS; Amazon SNS; RDS Database.
Forbidden relationships: AWS Lambda AddressFinder -> AWS Lambda PhoneFinder.
Forbidden resource types: `aws_sqs_queue`, `aws_sns_topic`, `aws_db_instance`.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-04-analytics-catalog

![pt1-04-analytics-catalog](fixtures/pt1-04-analytics-catalog.png)

Classification: `ARCHITECTURE_DIAGRAM`. Layout: Dense rectangular layout; long crossing data/SQL lines; distinct metadata and object flow.

Source basis: AWS documentation specifies Athena querying S3 using Glue metadata and optional crawler schema inference. This drawing explicitly selects crawler, a named workgroup and a separate S3 query-output bucket.

- [Use AWS Glue Data Catalog to connect to your data - Amazon Athena](https://docs.aws.amazon.com/athena/latest/ug/data-sources-glue.html)

Required components: Amazon S3 source CSV objects; AWS Glue crawler; AWS Glue Data Catalog lake database; Amazon Athena analytics workgroup; Amazon S3 query results; Analyst SQL client.

Required relationships:

- Amazon S3 source CSV objects -> AWS Glue crawler
- AWS Glue crawler -> AWS Glue Data Catalog lake database
- AWS Glue Data Catalog lake database -> Amazon Athena analytics workgroup
- Amazon S3 source CSV objects -> Amazon Athena analytics workgroup
- Amazon Athena analytics workgroup -> Amazon S3 query results
- Analyst SQL client -> Amazon Athena analytics workgroup

Required resource types: `aws_s3_bucket`, `aws_glue_crawler`, `aws_glue_catalog_database`, `aws_athena_workgroup`.

Required semantic intent:

- Separate source and query-output buckets; crawler populates the Glue database with schema; Athena workgroup queries source objects and writes query results.

Acceptable variants: Metadata lookup may be described as Athena -> Glue; read-direction descriptions may reverse the data-flow arrow when the read semantics remain explicit. Crawler may create tables dynamically.

Allowed support types: `aws_glue_catalog_table`, `aws_iam_role`, `aws_iam_policy`.

Forbidden components: RDS Database; AWS Lambda.
Forbidden relationships: AWS Glue crawler moves objects into AWS Glue Data Catalog.
Forbidden resource types: `aws_db_instance`, `aws_lambda_function`.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-05-private-web-fleet

![pt1-05-private-web-fleet](fixtures/pt1-05-private-web-fleet.png)

Classification: `ARCHITECTURE_DIAGRAM`. Layout: Largest case; nested AZ/subnet boundaries; small routing text; shared versus per-AZ resource distinction.

Source basis: Independent redraw of documented two-AZ public/private VPC, ALB and Auto Scaling private servers, one NAT per AZ and S3 gateway endpoint. Select IPv4-only explicitly; two ALB nodes represent one logical load balancer.

- [Example: VPC with servers in private subnets and NAT - Amazon Virtual Private Cloud](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-example-private-subnets-nat.html)

Required components: VPC; Public subnet A; Public subnet B; Private subnet A; Private subnet B; Internet gateway; Application Load Balancer; NAT gateway A; NAT gateway B; EC2 server A; EC2 server B; Auto Scaling group; S3 gateway VPC endpoint.

Required relationships:

- Internet clients -> Application Load Balancer
- Application Load Balancer -> EC2 server A
- Application Load Balancer -> EC2 server B
- EC2 server A -> NAT gateway A
- EC2 server B -> NAT gateway B
- EC2 servers -> S3 gateway VPC endpoint

Required resource types: `aws_vpc`, `aws_subnet`, `aws_internet_gateway`, `aws_lb`, `aws_autoscaling_group`, `aws_launch_template`, `aws_nat_gateway`, `aws_route_table`, `aws_vpc_endpoint`, `aws_security_group`.

Required semantic intent:

- Two public and two private subnets across two AZs; one logical ALB and ASG; ASG instances are supplied by a launch template, not mandatory standalone aws_instance resources. One NAT per AZ, private egress routing and S3 gateway endpoint.

Acceptable variants: Route entries may be inline or standalone; ALB/ASG node placement can be expressed as shared resources. No IPv6 or egress-only gateway required.

Allowed support types: `aws_route`, `aws_route_table_association`, `aws_eip`, `aws_lb_listener`, `aws_lb_target_group`, `aws_vpc_endpoint_route_table_association`.

Forbidden components: RDS Database; EKS; CloudFront.
Forbidden relationships: Internet clients -> EC2 servers directly; EC2 server A -> NAT gateway B.
Forbidden resource types: `aws_db_instance`, `aws_eks_cluster`, `aws_cloudfront_distribution`.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-06-thumbnail-pipeline

![pt1-06-thumbnail-pipeline](fixtures/pt1-06-thumbnail-pipeline.png)

Classification: `ARCHITECTURE_DIAGRAM`. Layout: Portrait layout; reverse read edge; long event annotation.

Source basis: The S3 Lambda documentation describes object-created notifications, invocation permission and using two buckets to prevent recursive output triggers. Thumbnail processing is an explicitly labeled instance of that pattern.

- [Process Amazon S3 event notifications with Lambda - AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/with-s3.html)

Required components: Uploader camera client; Amazon S3 original images; AWS Lambda thumbnail processor; Amazon S3 thumbnail output.

Required relationships:

- Uploader camera client -> Amazon S3 original images
- Amazon S3 original images -> AWS Lambda thumbnail processor
- AWS Lambda thumbnail processor reads Amazon S3 original images
- AWS Lambda thumbnail processor -> Amazon S3 thumbnail output

Required resource types: `aws_s3_bucket`, `aws_s3_bucket_notification`, `aws_lambda_function`, `aws_lambda_permission`, `aws_iam_role`.

Required semantic intent:

- Two distinct buckets; ObjectCreated notification on the source only; S3 invocation permission and Lambda execution-role access to read source/write output.

Acceptable variants: Service synonyms and equivalent IAM support resources are acceptable; merging buckets is excluded by this explicitly chosen two-bucket diagram.

Allowed support types: `aws_iam_policy`, `aws_iam_role_policy`, `aws_s3_bucket_public_access_block`.

Forbidden components: Amazon SQS; RDS Database.
Forbidden relationships: Amazon S3 thumbnail output -> AWS Lambda thumbnail processor.
Forbidden resource types: `aws_sqs_queue`, `aws_db_instance`.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-07-unresolved-design

![pt1-07-unresolved-design](fixtures/pt1-07-unresolved-design.png)

Classification: `AMBIGUOUS`. Layout: Wide planning sketch; visible question marks and unresolved decisions.

Source basis: Original repository-owned design-notes control. It deliberately leaves provider, services and connectivity decisions unresolved.

Terraform must not be generated: Cloud provider, concrete deployable services and trust boundaries are explicitly TBD. Do not invent deployable Terraform from an unresolved design.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-08-partial-export

![pt1-08-partial-export](fixtures/pt1-08-partial-export.png)

Classification: `AMBIGUOUS`. Layout: Clipped edge boxes, incomplete labels, small remaining topology.

Source basis: Original repository-owned partial-export control with intentionally missing provider/service labels and boundary panels. Not cropped from a third-party asset.

Terraform must not be generated: The visible generic processing box and clipped endpoints do not identify a provider or resource intent. Missing topology cannot be safely inferred.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-09-sprint-board

![pt1-09-sprint-board](fixtures/pt1-09-sprint-board.png)

Classification: `NON_ARCHITECTURE_IMAGE`. Layout: Kanban screenshot style; cloud terminology without topology.

Source basis: Original repository-owned project-planning board control. Contains realistic cloud service mentions in task cards, with no architecture connections.

Terraform must not be generated: Task ownership/status columns and service-name mentions describe work items, not deployable components or relationships.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## pt1-10-workshop-table

![pt1-10-workshop-table](fixtures/pt1-10-workshop-table.png)

Classification: `NON_ARCHITECTURE_IMAGE`. Layout: Document/table style; ordinary nontechnical content.

Source basis: Original repository-owned ordinary workshop schedule/table control. No copied personal or business information.

Terraform must not be generated: A timetable and supplies list contain no computing architecture or infrastructure resource intent.

Truth uncertainty: no unresolved factual guess in this candidate. Human confirmation of the above remains required.

## Human freeze checkpoint

Review all ten cases at the same candidate identity. Human approval must explicitly bind the truth and bytes; until then `truthApprovedBy` and `truthFrozenAt` remain null, program state is `HUMAN_REQUIRED`, and PT-2 remains ineligible. Merging the candidate PR does not imply truth approval or authorize live measurement. PT-2 requires its separate `LIVE_REALISTIC_BASELINE` checkpoint even after truth freeze.

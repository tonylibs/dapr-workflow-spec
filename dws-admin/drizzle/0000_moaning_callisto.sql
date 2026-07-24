CREATE TABLE "workflow_definitions" (
	"name" text NOT NULL,
	"version" text NOT NULL,
	"status" text NOT NULL,
	"created_at" timestamp with time zone NOT NULL,
	CONSTRAINT "workflow_definitions_name_version" UNIQUE("name","version")
);
--> statement-breakpoint
CREATE TABLE "deployments" (
	"workflow" text NOT NULL,
	"version" text NOT NULL,
	"step_services" jsonb NOT NULL,
	"orchestrator_app_id" text NOT NULL,
	"status" text NOT NULL,
	"drained_at" timestamp with time zone,
	CONSTRAINT "deployments_workflow_version" UNIQUE("workflow","version")
);
--> statement-breakpoint
CREATE TABLE "workflow_instances" (
	"instance_id" text PRIMARY KEY NOT NULL,
	"workflow" text NOT NULL,
	"version" text NOT NULL,
	"app_id" text NOT NULL,
	"status" text NOT NULL,
	"started_at" timestamp with time zone,
	"ended_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "task_events" (
	"id" text PRIMARY KEY NOT NULL,
	"instance_id" text NOT NULL,
	"task_name" text NOT NULL,
	"type" text NOT NULL,
	"status" text NOT NULL,
	"timestamp" timestamp with time zone NOT NULL,
	"error" text
);
--> statement-breakpoint
CREATE TABLE "processed_events" (
	"event_id" text PRIMARY KEY NOT NULL,
	"processed_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
CREATE INDEX "task_events_instance_id_idx" ON "task_events" USING btree ("instance_id");
ALTER TABLE visit_reviews
    ADD COLUMN route_plan_id CHAR(36) NULL,
    ADD COLUMN route_rating VARCHAR(10) NULL,
    ADD CONSTRAINT fk_visit_review_route_plan
        FOREIGN KEY (route_plan_id) REFERENCES route_plans(route_plan_id);

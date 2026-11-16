plugins {
    id("com.gradleup.nmcp.aggregation") version "1.6.2"
}

// Both Boot-specific modules are aggregated into a single Central Portal deployment.
// `publishAggregationToCentralPortal` publishes a release; use
// `publishAggregationToCentralSnapshots` for snapshots.
nmcpAggregation {
    centralPortal {
        username.set(providers.gradleProperty("centralUsername"))
        password.set(providers.gradleProperty("centralPassword"))
        publishingType.set("AUTOMATIC")
        publicationName.set("aws-appconfig-config")
    }
}

dependencies {
    add("nmcpAggregation", project(":aws-appconfig-config"))
    add("nmcpAggregation", project(":aws-appconfig-config-boot4"))
}

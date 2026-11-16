rootProject.name = "aws-appconfig-config-parent"

include("aws-appconfig-config")
include("aws-appconfig-config-boot4")
include("example")

project(":aws-appconfig-config").projectDir = file("boot3")
project(":aws-appconfig-config-boot4").projectDir = file("boot4")

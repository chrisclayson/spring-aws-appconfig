# AWS Emulator for the AppConfig integration test.
# The emulator does not publish an image, so it is built from the RPM on first use.
FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal

RUN ARCH="$(uname -m)" \
    && case "$ARCH" in \
         x86_64) RPM="aws-emulator-x86_64.rpm" ;; \
         aarch64) RPM="aws-emulator-aarch64.rpm" ;; \
         *) echo "Unsupported architecture: $ARCH" >&2; exit 1 ;; \
       esac \
    && curl -fsSL -o /tmp/aws-emulator.rpm \
         "https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/latest/${RPM}" \
    && rpm -i /tmp/aws-emulator.rpm \
    && rm -f /tmp/aws-emulator.rpm \
    && mkdir -p /home/emulator \
    && chown 1000:1000 /home/emulator

USER 1000
ENV HOME=/home/emulator
ENV EMULATOR_TELEMETRY=off
EXPOSE 9297
ENTRYPOINT ["aws-emulator", "-b", "0.0.0.0", "-p", "9297"]

# ─── Runtime image ────────────────────────────────────────────────────────────
# The JAR is built by Maven in CI and passed in via the build context (target/).
# Using a slim JRE image keeps the final image small (~200 MB vs ~600 MB for JDK).
FROM eclipse-temurin:17-jre-alpine

# Non-root user for security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the fat JAR produced by spring-boot-maven-plugin
COPY target/*.jar app.jar

# Ensure the non-root user owns the files
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

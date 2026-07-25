FROM ghcr.io/graalvm/graalvm-community:25
RUN mkdir -p /opt/app
COPY japp.jar /opt/app/
CMD ["java", "-jar", "/opt/app/japp.jar"]
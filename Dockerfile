FROM openjdk:13-jdk

RUN mkdir -p /aws && \
      yum -y update && \
      yum install -y bash curl less openssl vim apt-transport-https ca-certificates software-properties-common argon2 && \
      mkdir -p /app/libs && \
      mkdir -p /keyssrv

WORKDIR "/keyssrv"
#configuration
COPY env/prod/resources/config.edn /keyssrv/
COPY env/prod/resources/logback.xml /keyssrv/

#public html and other private resources
COPY resources /keyssrv/resources
COPY entry-point.sh /keyssrv/entry-point.sh

#java binaries and scripts
COPY target/keyssrv.jar /keyssrv/keyssrv.jar
COPY target/lib /keyssrv/lib/

## ensure that we do not ship test certificates
#RUN rm -rf ~/.data && \
#    find ~/ -iname "*.pem" -exec rm -rf {} \; && \
#    find /keyssrv/ -iname "*.pem" -exec rm -rf {} \;

EXPOSE 3001

CMD ["/keyssrv/entry-point.sh"]

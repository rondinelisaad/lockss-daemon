FROM ubuntu:14.04
MAINTAINER Infrastructure SciELO <infra@scielo.org>

RUN apt-get update && \
apt-get -y install openjdk-7-jdk python ant ant-optional git vim liblocale-msgfmt-perl gettext junit debconf-i18n libplexus-containers-java i18nspector  libgettextpo-dev python-polib locales

ENV ANT_HOME /usr/share/ant
ENV PATH ${PATH}:${ANT_HOME}/bin
ENV JAVA_HOME /usr/lib/jvm/java-7-openjdk-amd64/
ENV LANG en_US.UTF-8
ENV LC_ALL en_US.UTF-8
ENV LC_MESSAGES en_EN.UTF-8
ENV LC_MESSAGES en_US.UTF-8
ENV LANGUAGE en_US.UTF-8

RUN locale-gen en_US.UTF-8 && \
dpkg-reconfigure --frontend=noninteractive locales

VOLUME /data
COPY docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]

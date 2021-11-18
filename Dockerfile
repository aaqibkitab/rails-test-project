FROM registry.access.redhat.com/ubi8/s2i-core:1-249

ENV NODEJS_VER=14
RUN curl --silent --location https://dl.yarnpkg.com/rpm/yarn.repo | tee /etc/yum.repos.d/yarn.repo
RUN yum -y module enable nodejs:$NODEJS_VER && \
  INSTALL_PKGS="autoconf \
  automake \
  bzip2 \
  gcc-c++ \
  gd-devel \
  gdb \
  git \
  libcurl-devel \
  libpq-devel \
  libxml2-devel \
  libxslt-devel \
  lsof \
  make \
  mariadb-connector-c-devel \
  openssl-devel \
  patch \
  procps-ng \
  npm \
  yarn \
  redhat-rpm-config \
  sqlite-devel \
  unzip \
  wget \
  which \
  zlib-devel" && \
  yum install -y --setopt=tsflags=nodocs $INSTALL_PKGS && \
  rpm -V $INSTALL_PKGS && \
  yum -y clean all --enablerepo='*'

ENV RUBY_MAJOR_VERSION=2 \
    RUBY_MINOR_VERSION=7

ENV RUBY_VERSION="${RUBY_MAJOR_VERSION}.${RUBY_MINOR_VERSION}" \
    RUBY_SCL_NAME_VERSION="${RUBY_MAJOR_VERSION}${RUBY_MINOR_VERSION}"
RUN yum -y module enable ruby:$RUBY_VERSION && \
    INSTALL_PKGS=" \
    libffi-devel \
    ruby \
    ruby-devel \
    rubygem-rake \
    rubygem-bundler \
    redhat-rpm-config \
    " && \
    yum install -y --setopt=tsflags=nodocs ${INSTALL_PKGS} && \
    yum -y clean all --enablerepo='*' && \
    rpm -V ${INSTALL_PKGS}

WORKDIR /app

ENV BUNDLE_PATH /gems
# install gems
COPY Gemfile .
COPY Gemfile.lock .

COPY package.json .
COPY yarn.lock .

RUN yarn install
RUN bundle install

COPY . /app/
EXPOSE 8080
ADD entrypoint.sh /usr/bin/
RUN chmod +x /usr/bin/entrypoint.sh
ENTRYPOINT ["entrypoint.sh"]


#ENTRYPOINT ["bin/rails"]
#CMD ["s", "-b", "0.0.0.0"]



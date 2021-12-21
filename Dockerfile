FROM registry.access.redhat.com/ubi8/s2i-core:1-249 as base

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
  openssl-devel \
  patch \
  procps-ng \
  npm \
  yarn \
  redhat-rpm-config \
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

FROM base as dependencies
WORKDIR /app

ENV BUNDLE_PATH /gems
# install gems
COPY Gemfile .
COPY Gemfile.lock .

COPY package.json .
COPY yarn.lock .

RUN yarn install
RUN bundle install --path vendor/bundle

RUN rm -rf /vendor/bundle/ruby/2.7.0/cache/*.gem

COPY . /app/

# Remove folders not needed in resulting image
RUN rm -rf node_modules tmp/cache vendor/assets lib/assets spec


FROM base

COPY --from=dependencies /app /app

WORKDIR /app
ENV RAILS_ENV=production

RUN chown -R 1001:0 /app && chmod -R ug+rwx /app && \
    rpm-file-permissions
RUN RAILS_ENV=production SECRET_KEY_BASE=foo bin/rails assets:precompile
USER 1001
ENTRYPOINT ["bin/rails"]
CMD ["s", "-b", "0.0.0.0"]

EXPOSE 3000

FROM registry.access.redhat.com/ubi8/s2i-core:1-249

ENV NODEJS_VER=14

ENV RUBY_MAJOR_VERSION=2 \
    RUBY_MINOR_VERSION=7

ENV RUBY_VERSION="${RUBY_MAJOR_VERSION}.${RUBY_MINOR_VERSION}" \
    RUBY_SCL_NAME_VERSION="${RUBY_MAJOR_VERSION}${RUBY_MINOR_VERSION}"

#FROM base as dependencies
WORKDIR /app

ENV BUNDLE_PATH /gems
# install gems
#COPY Gemfile .
#COPY Gemfile.lock .

#COPY package.json .
#COPY yarn.lock .

#RUN yarn install
#RUN bundle install --without development test --path vendor/bundle

#RUN rm -rf /vendor/bundle/ruby/2.7.0/cache/*.gem

#COPY . /app/

# Remove folders not needed in resulting image
#RUN rm -rf node_modules tmp/cache vendor/assets lib/assets spec


#WORKDIR /app
ENV RAILS_ENV=production

#RUN chown -R 1001:0 /app && chmod -R ug+rwx /app && \
#    rpm-file-permissions
#RUN RAILS_ENV=production SECRET_KEY_BASE=foo bin/rails assets:precompile
USER 1001
ENTRYPOINT ["tail", "-f", "/dev/null"]

EXPOSE 3000

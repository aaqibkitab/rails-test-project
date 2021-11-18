#!/bin/sh
set -e

rm -f /app/tmp/pids/server.pid

# https://github.com/rails/webpacker/issues/2674

bin/rails assets:precompile

#bin/rails db:migrate --trace

bin/rails server -b 0.0.0.0 -p 8080

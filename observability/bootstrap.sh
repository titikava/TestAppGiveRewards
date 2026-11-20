#!/bin/sh
set -eu
es=http://elasticsearch:9200
kb=http://kibana:5601
put() {
    curl --silent --show-error --fail-with-body -X PUT -H 'Content-Type: application/json' --data-binary @- "$1"
}
put "$es/_ilm/policy/rewards-api-week" <<'JSON'
{"policy":{"phases":{"delete":{"min_age":"7d","actions":{"delete":{}}}}}}
JSON
put "$es/_index_template/rewards-api" < /setup/index-template.json

# A stable ID keeps links and saved searches usable after a restart.
status=$(curl -sS -o /dev/null -w '%{http_code}' "$kb/api/data_views/data_view/rewards-api")
case "$status" in
    200) ;;
    404)
        curl -fsS -H 'kbn-xsrf: setup' -H 'Content-Type: application/json' \
            -d '{"data_view":{"id":"rewards-api","title":"rewards-api-*","name":"Rewards API","timeFieldName":"@timestamp","allowNoIndex":true}}' \
            "$kb/api/data_views/data_view" > /dev/null ;;
    *) echo "Could not read Rewards API data view (HTTP $status)" >&2; exit 1 ;;
esac
curl -fsS -H 'kbn-xsrf: setup' -H 'Content-Type: application/json' \
    -d '{"data_view_id":"rewards-api","force":false}' "$kb/api/data_views/default"

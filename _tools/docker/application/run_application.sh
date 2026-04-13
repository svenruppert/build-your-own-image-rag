docker run \
       --rm \
       --name run \
       -p 7070:7070 \
       projectname/application:latest \

# curl http://127.0.0.1:7070/upper/value/name

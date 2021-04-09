#!/bin//bash

EXTRA=()
INSTANCES_NUMBER=1
for i in "$@"
do
case $i in
    --port=*)
      PORT="${i#*=}"
      shift # past arguments
    ;;
    -p=*|--profile=*)
    PROFILES="${i#*=}"
    shift # past argument
    ;;
    -in=*|--instances-number=*)
    INSTANCES_NUMBER="${i#*=}"
    shift # past argument
    ;;
    -v|--verbous)
    VERBOUS=true
    shift # past argument
    ;;
    *)    # unknown option
    EXTRA+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${EXTRA[@]}" # restore positional parameters


if [[ "${EXTRA[0]}" == "start" ]]; then
  echo "STARTING SERVICE :                    "
  echo "PROFILES         = ${PROFILES}        "
  echo "INSTANCES NUMBER = ${INSTANCES_NUMBER}"
  echo "SERVICE          = ${EXTRA[1]}        "
  echo "BASE PORT        = ${PORT}            "

  if [[ "${VERBOUS}" == "true" ]]; then
    if [[ $((INSTANCES_NUMBER)) -gt 1 ]]; then
      for i in $(seq 0 "$((INSTANCES_NUMBER - 1))")
      do
        eval "java -Dspring.profiles.active=default,${PROFILES} -Dserver.port=$((PORT + i)) -Dspring.application.id=${i} -jar ${EXTRA[1]} &"
      done
    else
      eval "java -Dspring.profiles.active=default,${PROFILES} -jar ${EXTRA[1]} &"
    fi
  else
    if [[ $((INSTANCES_NUMBER)) -gt 1 ]]; then
      for i in $(seq 0 "$((INSTANCES_NUMBER - 1))")
      do
        eval "java -Dspring.profiles.active=default,${PROFILES} -Dserver.port=$((PORT + i)) -Dspring.application.id=${i} -jar ${EXTRA[1]} &>/dev/null &"
      done
    else
      eval "java -Dspring.profiles.active=default,${PROFILES} -jar ${EXTRA[1]} &>/dev/null &"
    fi
  fi
elif [[ "${EXTRA[0]}" == "stop" ]]; then
  eval "pkill -9 -f ${EXTRA[1]}"
elif [[ "${EXTRA[0]}" == "status" ]]; then
  eval "ps aux | grep java"
else
  echo "Unknown command"
fi

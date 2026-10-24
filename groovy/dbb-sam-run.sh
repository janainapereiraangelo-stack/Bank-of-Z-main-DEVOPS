#!/bin/bash
###############################################################################
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2021. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
###############################################################################

## Override the defaults by exporting these variables before running the script:
##   HLQ, USS_USER, DBB_HOME_REMOTE, DBB_JAVA_HOME_REMOTE (see dbb-sam-prepare-and-build.sh)

set -e -x
HLQ="${HLQ:-RAFAELT}"
USS_USER="${USS_USER:-rafaelt}"
DBB_HOME_REMOTE="${DBB_HOME_REMOTE:-}"   # empty = use DBB_HOME already defined in the remote shell
DBB_JAVA_HOME_REMOTE="${DBB_JAVA_HOME_REMOTE:-}"
SAM_DIR="${SAM_DIR:-/u/$USS_USER/projects/zopeneditor-sample}"
BUILD_DIR="$SAM_DIR/BUILD"
SSHPROFILE="" # to use a non-default profile use "--ssh-profile profileName"

REMOTE_ENV=""
if [ -n "$DBB_HOME_REMOTE" ]; then
  REMOTE_ENV="export DBB_HOME=$DBB_HOME_REMOTE;"
fi
if [ -n "$DBB_JAVA_HOME_REMOTE" ]; then
  REMOTE_ENV="$REMOTE_ENV export JAVA_HOME=$DBB_JAVA_HOME_REMOTE; export PATH=$DBB_JAVA_HOME_REMOTE/bin:\$PATH;"
fi
zowe uss issue ssh "$REMOTE_ENV \$DBB_HOME/bin/groovyz -DBB_PERSONAL_DAEMON $BUILD_DIR/dbb-sam-run.groovy $HLQ $SAM_DIR" $SSHPROFILE

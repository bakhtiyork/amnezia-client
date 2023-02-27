#!/bin/bash
echo "Build script started ..."

set -o errexit -o nounset

# Hold on to current directory
PROJECT_DIR=$(pwd)
DEPLOY_DIR=$PROJECT_DIR/deploy

mkdir -p $DEPLOY_DIR/build
BUILD_DIR=$DEPLOY_DIR/build

echo "Project dir: ${PROJECT_DIR}" 
echo "Build dir: ${BUILD_DIR}"

APP_NAME=AmneziaVPN
APP_FILENAME=$APP_NAME.app
APP_DOMAIN=org.amneziavpn.package

OUT_APP_DIR=$BUILD_DIR/client
BUNDLE_DIR=$OUT_APP_DIR/$APP_FILENAME

# Seacrh Qt
if [ -z "${QT_VERSION+x}" ]; then
  QT_VERSION=6.4.1;
  QT_BIN_DIR=$HOME/Qt/$QT_VERSION/$ANDROID_CURRENT_ARCH/bin
fi

echo "Using Qt in $QT_BIN_DIR"
echo "Using Android SDK in $ANDROID_SDK_ROOT"
echo "Using Android NDK in $ANDROID_NDK_ROOT"

# Build App
echo "Building App..."
cd $BUILD_DIR

echo "HOST Qt: $QT_HOST_PATH"

if [[ "$ACTIONS_BUILD_TYPE" == "release" ]]; then
  BUILD_TYPE_FOR_QTCMAKE="Release"
  BUILD_TYPE_FOR_CMAKE="release"
  BUILD_TYPE_FOR_ANDROIDDEPLOYQT="--release"
  BUILD_TYPE_FOR_COPYING="release"
  ARCH_SUFFIX=""
  ARTIFACT_TYPE="bundle"
else
  BUILD_TYPE_FOR_QTCMAKE="Debug"
  BUILD_TYPE_FOR_CMAKE="debug"
  BUILD_TYPE_FOR_ANDROIDDEPLOYQT="--debug"
  BUILD_TYPE_FOR_COPYING="debug"
  ARCH_SUFFIX="-${ANDROID_CURRENT_ARCH}"
  ARTIFACT_TYPE="apk"
fi

$QT_BIN_DIR/qt-cmake -S $PROJECT_DIR \
   -DQT_NO_GLOBAL_APK_TARGET_PART_OF_ALL="ON" \
   -DQT_HOST_PATH=$QT_HOST_PATH \
   -DCMAKE_BUILD_TYPE=$BUILD_TYPE_FOR_QTCMAKE

cmake --build . --config $BUILD_TYPE_FOR_CMAKE

echo "............${ARTIFACT_EXTENSION} generation.................."
cd $OUT_APP_DIR

ANDROID_DEPLOYQT_PARAMETERS="--output $OUT_APP_DIR/android-build \
    --gradle \
    $BUILD_TYPE_FOR_ANDROIDDEPLOYQT \
    --input android-AmneziaVPN-deployment-settings.json \
    --android-platform android-33"

if [[ "$ACTIONS_BUILD_TYPE" == "release" ]]; then
  ANDROID_DEPLOYQT_PARAMETERS+=" --aab"
fi

$QT_HOST_PATH/bin/androiddeployqt ${ANDROID_DEPLOYQT_PARAMETERS}
   
echo "............Copying ${ARTIFACT_EXTENSION}.................."
VAR_COPY_FROM=$OUT_APP_DIR/android-build/build/outputs/${ARTIFACT_TYPE}/${ACTIONS_BUILD_TYPE}/android-build-${BUILD_TYPE_FOR_COPYING}.${ARTIFACT_EXTENSION}
VAR_COPY_TO=$PROJECT_DIR/AmneziaVPN-${BUILD_TYPE_FOR_CMAKE}${ARCH_SUFFIX}.${ARTIFACT_EXTENSION}

echo "Copying from $VAR_COPY_FROM to $VAR_COPY_TO"

cp $VAR_COPY_FROM $VAR_COPY_TO

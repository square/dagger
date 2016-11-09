# see https://coderwall.com/p/9b_lfq

if [ "$TRAVIS_REPO_SLUG" == "google/dagger" ] && \
   [ "$TRAVIS_JDK_VERSION" == "$JDK_FOR_PUBLISHING" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  echo -e "Publishing maven snapshot...\n"

  mvn clean source:jar deploy --settings="util/settings.xml" -DskipTests=true -Dinvoker.skip=true -Dmaven.javadoc.skip=true

  echo -e "Published maven snapshot"
else
  echo -e "Not publishing snapshot for jdk=${TRAVIS_JDK_VERSION} and branch=${TRAVIS_BRANCH}"
fi

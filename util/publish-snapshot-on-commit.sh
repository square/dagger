# see https://coderwall.com/p/9b_lfq

if [ "$TRAVIS_REPO_SLUG" == "google/dagger" -a \
   "$TRAVIS_JDK_VERSION" == "oraclejdk7" -a \
   "$TRAVIS_PULL_REQUEST" == "false" -a \
   "$TRAVIS_BRANCH" == "master" ]; then
  echo -e "Publishing maven snapshot...\n"

  cd ${HOME}
  git clone --quiet --branch=travis https://github.com/google/dagger travis > /dev/null
  
  mvn clean deploy --settings="$HOME/travis/settings.xml" -DskipTests=true -Dinvoker.skip=true -Dmaven.javadoc.skip=true

  echo -e "Published maven snapshot"
fi

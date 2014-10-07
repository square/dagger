# see http://benlimmer.com/2013/12/26/automatically-publish-javadoc-to-gh-pages-with-travis-ci/ for details

if [ "$TRAVIS_REPO_SLUG" == "google/dagger" ] && \
   [ "$TRAVIS_JDK_VERSION" == "oraclejdk7" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  echo -e "Publishing javadoc...\n"
  mvn javadoc:jar

  cd $HOME
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"
  git clone --quiet --branch=gh-pages https://${GH_TOKEN}@github.com/google/dagger gh-pages > /dev/null
  
  cd gh-pages
  git rm -rf api/latest 
  mkdir -p api/latest
  unzip ${HOME}/core/target/*-SNAPSHOT-javadoc.jar -d api/latest
  git add -f api/latest
  git commit -m "Lastest javadoc on successful travis build $TRAVIS_BUILD_NUMBER auto-pushed to gh-pages"
  git push -fq origin gh-pages > /dev/null

  echo -e "Published Javadoc to gh-pages.\n"
fi

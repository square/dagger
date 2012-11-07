#!/bin/bash
#
# Deploys the current Dagger website to the gh-pages branch of the GitHub
# repository. To test the site locally before deploying run `jekyll --server`
# in the website/ directory.

set -ex

DIR=temp-dagger-clone

# Delete any existing temporary website clone
rm -rf $DIR

# Clone the current repo into temp folder
git clone git@git.squareup.com:square/dagger.git $DIR

# Move working directory into temp folder
cd $DIR

# Checkout and track the gh-pages branch
git checkout -t origin/gh-pages

# Delete everything
git rm -rf * .*

# Copy website files from real repo
cp -R ../website/* .

# Stage all files in git and create a commit
git add .
git commit -m "Website at ${date}"

# Push the new files up to GitHub
git push origin gh-pages

# Delete our temp folder
cd ..
rm -rf $DIR

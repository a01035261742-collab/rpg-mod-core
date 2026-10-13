#!/bin/bash
# 최신 CI 결과 확인
R=a01035261742-collab/rpg-mod-core
ID=$(gh api repos/$R/actions/runs --jq '.workflow_runs[0].id')
for i in $(seq 1 45); do s=$(gh api repos/$R/actions/runs/$ID --jq '.status+" "+(.conclusion//"")'); case "$s" in completed*) echo "$s"; break;; esac; sleep 20; done
J=$(gh api repos/$R/actions/runs/$ID/jobs --jq '.jobs[0].id')
gh api repos/$R/check-runs/$J/annotations --jq '.[] | select(.title=="build") | .message'

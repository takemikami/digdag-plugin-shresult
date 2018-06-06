# digdag-plugin-shresult

[![Build Status](https://travis-ci.org/takemikami/digdag-plugin-shresult.svg?branch=master)](https://travis-ci.org/takemikami/digdag-plugin-shresult)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/3e8669ca82e243ff977d05292ed89cc2)](https://www.codacy.com/app/takemikami/digdag-plugin-shresult?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=takemikami/digdag-plugin-shresult&amp;utm_campaign=Badge_Grade)
[![Coverage Status](https://coveralls.io/repos/github/takemikami/digdag-plugin-shresult/badge.svg?branch=master)](https://coveralls.io/github/takemikami/digdag-plugin-shresult?branch=master)

digdag-plugin-shresult is plugin storing output of shell to digdag store.

## Getting Started

setup digdag, see https://www.digdag.io/

create digdag workflow, following code is sample workflow.

sample.dig ... for each by resultset of  BigQuery. 

```
_export:
  plugin:
    repositories:
      - https://jitpack.io
    dependencies:
      - com.github.takemikami:digdag-plugin-shresult:0.0.1

+step1:
  sh_result>: |
    bq query --format=json 'select name from dataset1.table1 limit 10'
  destination_variable: resultset
  stdout_format: json-list-map

+step2:
  for_each>:
    rv: ${resultset}
  _parallel:
    true
  _do:
    echo>: ${rv.name}
```

run sample workflow.

```
digdag run sample.dig
```

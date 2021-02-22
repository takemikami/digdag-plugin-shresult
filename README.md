# digdag-plugin-shresult

[![JitPack](https://jitpack.io/v/takemikami/digdag-plugin-shresult.svg)](https://jitpack.io/#takemikami/digdag-plugin-shresult)
[![Build Status](https://travis-ci.org/takemikami/digdag-plugin-shresult.svg?branch=master)](https://travis-ci.org/takemikami/digdag-plugin-shresult)
[![Coverage Status](https://coveralls.io/repos/github/takemikami/digdag-plugin-shresult/badge.svg?branch=master)](https://coveralls.io/github/takemikami/digdag-plugin-shresult?branch=master)

digdag-plugin-shresult is plugin storing output of shell to digdag store.

## Getting Started

setup digdag, see https://www.digdag.io/

create digdag workflow, following code is sample workflow.

sample.dig ... for each by resultset of BigQuery.

```
_export:
  plugin:
    repositories:
      - https://jitpack.io
    dependencies:
      - com.github.takemikami:digdag-plugin-shresult:0.0.3

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

## Usage

### sh_result>: Shell scripts and store output

sh_result> operator runs a shell scripts and set output to digdag store.

#### Options:

- sh_result>: COMMAND [ARGS...]

  Name of the command to run.

- destination_variable: Name

  Specifies a digdag-variable to store the standard output of script in.

- stdout_format: Name

  Type of standard output format.

   - text
   - newline-delimited
   - space-delimited
   - json-list-map

#### Stdout Formats

- text ... set each stdout to single value.

  example
  ```
  text message
  ```

- newline-delimited ... set newline-delimited stdout to list.

  example
  ```
  item1
  item2
  item3
  ```

- space-delimited ... set space-delimited stdout to list.

  example
  ```
  item1 item2 item3
  ```

- json-list-map ... set json-based stdout to list of map.

  example
  ```
  [
    {
      "id": "001",
      "name": "item1"
    },
    {
      "id": "002",
      "name": "item2"
    }
  ]
  ```

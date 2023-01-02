# aws.console

TODO <gif demo here>

## Usage

```
aws.console --profile production
aws.console --profile production --print-creds
aws.console --profile production --print-url

;; Open console at specific service and endpoint
aws.console --profile production --url https://ap-northeast-2.console.aws.amazon.com/gamelift/home?region=ap-northeast-2#/

;; Incognito browser mode (multiple sessions at same time, no open sessions after closing)
aws.console --profile production --incognito


aws.console --profile production --incognito


;; Other browser (chrome, brave, etc)
aws.console --profile production --browser brave
```


## Installation

Make sure you have followed Babashka's `bbin` [installation instructions](https://github.com/babashka/bbin#installation)

Then install this repo via

```
bbin install io.github.jeroenvandijk/aws.console
```

If minimal latency on the command line is important to you (without bbin wrapper 40ms less on my machine), install via file, see install-inline task in bb.edn.

## Roadmap

- [ ] Cleanup cli, with [Babashka's dispatch](https://github.com/babashka/cli/blob/main/API.md#dispatch)
- [ ] Minimal permissions for browsing console (default should be read, not maximum)
- [ ] Default config (for region, browser, incognito yes/no, sso, default policies)
- [ ] Documentation
- [ ] Test on other operating systems (Windows, Linux)

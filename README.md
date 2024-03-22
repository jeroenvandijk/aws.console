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

;; Other s (chrome, brave, etc)
aws.console --profile production --browser brave
```

## Installation

Make sure you have followed Babashka's `bbin` [installation instructions](https://github.com/babashka/bbin#installation)

Then install this repo via

```
bbin install io.github.jeroenvandijk/aws.console
```

If minimal latency on the command line is important to you (without bbin wrapper 40ms less on my machine), install via file, see install-inline task in bb.edn.

Next step is to create profiles for your different environments.

### Create profiles

Add profiles to your `~/.aws/credentials`.

Use [AWS Organizations](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_introduction.html) to be able to use STS to go from one account to the other.

An example below. `root-admin` is the main account that is connected via something like Okta. The other profiles are accessible via by using these credentials to create STS credentials.

By putting the profiles here you can open the browser console easily, but also other command line tools can use the generated credentials.

```
[root-admin]
credential_process = aws.console --account 111111111111 --role AdministratorAccess --region us-east-1 --sso-url "https://d-1234567890.awsapps.com/start" --print-creds

[production]
credential_process = aws.console --sts root-admin --account 222222222222 --role OrganizationAccountAccessRole --session-name "production" --region eu-central-1 --print-creds

[staging]
credential_process = aws.console --sts root-admin --account 333333333333 --role OrganizationAccountAccessRole --session-name "staging" --region eu-central-1 --print-creds
```

## Roadmap

- [ ] Cleanup cli, with [Babashka's dispatch](https://github.com/babashka/cli/blob/main/API.md#dispatch)
- [ ] Minimal permissions for browsing console (default should be read, not maximum)
- [ ] Default config (for region, browser, incognito yes/no, sso, default policies)
- [ ] Documentation
- [ ] Test on other operating systems (Windows, Linux)

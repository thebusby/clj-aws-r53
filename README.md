[![Build Status](https://buildhive.cloudbees.com/job/mrowe/job/clj-aws-r53/badge/icon)](https://buildhive.cloudbees.com/job/mrowe/job/clj-aws-r53/)

# clj-aws-r53

A Clojure library for accessing Amazon Route53, based on the official
AWS Java SDK and borrowing heavily from [clj-aws-ec2][] and James
Reeves's [clj-aws-s3][] library.

[clj-aws-ec2]: https://github.com/mrowe/clj-aws-ec2
[clj-aws-s3]: https://github.com/weavejester/clj-aws-s3

## Install

Add the following dependency to your `project.clj` file:

    [clj-aws-r53 "0.1.1"]

## Example

```clojure
(require '[aws.sdk.route53 :as route53])

(def cred {:access-key "...", :secret-key "..."})

```

### Exception handling

You can catch exceptions and extract details of the error condition:

```clojure
(try
  (route53/do-something cred "a thing")
  (catch Exception e (route53/decode-exception e)))
```

`route53/decode-exception` provides a map with the following keys:

    :error-code
    :error-type
    :service-name
    :status-code


## Documentation

* [API docs](http://mrowe.github.com/clj-aws-r53/)

## History


### 0.1.1

 * Initial release.


## License

Copyright (C) 2013 Michael Rowe

Distributed under the Eclipse Public License, the same as Clojure.

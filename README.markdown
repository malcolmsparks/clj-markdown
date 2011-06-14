# A Clojure Markdown library.

The purpose of this library is to provide Clojure with native support for
Markdown and other text-based authoring formats such as Org-mode.

## Rationale

Existing Markdown implementations in other languages are available to Clojure
developers but these all combine a parser front-end to Markdown with a backend
emitter (ie. HTML). For these to be used in Clojure the developer has to parse
the result back into Clojure structures for further processing.

A more efficient and flexible approach is to have the Markdown front-end create
a native Clojure data structure (maps and vectors) that can be manipulated by
functions and emitted into HTML using prxml, hiccup or a custom renderer. This
also makes it easy to support other output formats, for example, DocBook XML as
part of a professional publishing toolchain.

Another possibility would be to include rich text in a web application based on
Compojure (hiccup).

## Extensions

The overall aim is to pass all Markdown tests but while making it easier to
configure and create extensions that can adapt the library to different Markdown
flavors.

## Status

Currently this objective has not been reached and this library remains in a
work-in-progress 'alpha' state. Once the library fully passes the entire
Markdown test suite a version 1.0 will be released.

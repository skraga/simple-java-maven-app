#!/usr/bin/env groovy
def call(String person) {
    person = person ?: 'Stranger'
    echo "call(): Hello ${person}!"
}
return this;


def sayHello(Map args) {
    person = args.person ?: 'Stranger'
    echo "method(): Hello ${person}!"
}
return this;



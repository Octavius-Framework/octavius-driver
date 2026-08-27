# Octavius Documentation

*A Roman library was two libraries. Greek works stood in one room and Latin in another, each with a shelf-list
of its own, and a reader went to the room his text was in rather than searching both. What made them one
building was that the same reader often wanted both.*

Two sets of guides, because there are two things here and they are used separately. The **driver** speaks the
PostgreSQL wire protocol and stands on its own, with nothing above it. The **client** sits on the driver and is
optional; its pages are written in terms the driver's pages define, and point back at them rather than
restating them.

| Guides                         | For                                                                            |
|--------------------------------|--------------------------------------------------------------------------------|
| [The driver](driver/README.md) | Sessions, queries, transactions, the type system, COPY, Spring, and more       |
| [The client](client/README.md) | Query builders, transaction plans, `dynamic_dto`, annotation scanning          |

If you have not connected yet, the driver's [Quickstart](driver/quickstart.md) is where to start; nothing in
the client is reachable before that.

## API Reference

For signatures, properties and enum values, see the generated KDoc — rebuilt on every push to `master` and
covering every published module:

- [API Reference](https://octavius-framework.github.io/octavius-driver/)

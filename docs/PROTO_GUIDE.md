# Protobuf Guide

## Source of Truth

Files under `proto/` are the source of truth for request, response, shared model, and error wire contracts. Generated sources are build artifacts and must not be edited manually. Domain models remain independent Java types rather than aliases for generated messages.

## Organization

```text
proto/
└── game/
    ├── app/
    │   ├── common_init.proto
    │   ├── common_login.proto
    │   └── player_resource.proto
    ├── model/
    │   ├── account.proto
    │   ├── device.proto
    │   ├── player.proto
    │   ├── resource.proto
    │   └── inventory.proto
    └── common/
        └── error.proto
```

The Java generated-code namespace is:

```text
com.game.server.proto
```

Proto packages should follow the directory boundary, such as `game.app`, `game.model`, and `game.common`. Every file sets Java package options consistently so generated classes remain in `com.game.server.proto`.

## Contract Separation

- `game/app` defines use-case request and response messages.
- `game/model` defines reusable wire representations of model data.
- `game/common` defines cross-contract representations such as ApiError.
- Generated messages cross the transport boundary only; they are mapped to and from application or domain values.

## Naming Conventions

- File names use `lower_snake_case.proto`.
- Message and enum names use `UpperCamelCase`.
- Field names use `lower_snake_case`.
- Enum values use `UPPER_SNAKE_CASE` and include an explicit zero-value fallback such as `UNSPECIFIED`.
- Request and response messages use operation-specific names that make direction clear.
- Fields use domain vocabulary from [DOMAIN.md](DOMAIN.md).

## Field Numbers and Compatibility

Field numbers are permanent identifiers once a contract is published.

- Never renumber an existing field.
- Never reuse the number of a removed field.
- Never change a field to an incompatible wire type.
- Add new fields with unused numbers.
- Treat unknown fields as forward-compatible input.
- Do not make client behavior depend on the presence of fields introduced after its supported contract version.

When removing a field, reserve both its number and name:

```proto
message LoginResponse {
  reserved 7;
  reserved "legacy_token";
}
```

## Generated-Code Boundary

Generated classes must remain separate from domain models. Akka adapters decode and encode generated messages; mappers translate them at the application boundary. Domain records, store ports, and services must not expose generated message types. Regeneration replaces generated output without modifying handwritten domain code.

## Contract Versioning

`contract_version` identifies the published API schema understood by the server. Compatible additive changes may retain the active version according to the release policy. Breaking changes require an explicit contract-version transition and migration plan. `configuration_version` is separate and identifies game configuration, not schema compatibility.

## Change Examples

### Safe Changes

- Add a new optional scalar field with a new field number.
- Add a new message that existing messages do not require.
- Add an enum value when consumers handle unknown values safely.
- Reserve a removed field's name and number.

### Breaking Changes

- Renumber or reuse an existing field number.
- Change `string player_id` to an incompatible message type.
- Move a field into a `oneof` in a way that changes presence semantics.
- Rename or remove a field without preserving compatibility expectations.
- Change the meaning of an existing field while retaining its number.

## Tooling

Protocol Buffers 3.21.3 is the Slice 1 schema technology. Buf linting, Buf breaking-change checks, and automated generation may be added as optional future tooling unless already configured in the repository. Documentation must not claim those checks currently run until their configuration exists.

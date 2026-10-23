# Proto Studio

Language used by the schema editor and its local Node API.

## Language

**ProtoSchema**:
The shared definition of messages, fields, and enums used by client and server.
Editing a ProtoSchema changes the protocol contract, not the game configuration values represented by that contract.

**ProtoRevision**:
The complete set of ProtoSchemas at one specific repository commit, identified by its full commit SHA.
A committed schema change produces a new ProtoRevision; editing or exporting files does not change the selected ProtoRevision.

**ProtoDraft**:
An editable set of ProtoSchemas based on a selected ProtoRevision.
A ProtoDraft can be exported without being committed to the source repository.

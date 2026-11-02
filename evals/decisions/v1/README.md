# Typed decision smoke evaluation v1

Twenty-four original fixture states, eight per partition, cover contact, app, command/chat and act/ask choices in English and Arabic. These are frozen before any fitting. Each contact/app name is confined to one partition. The checksum inventory covers the manifest. These fixtures test the runtime and policy; eight final rows are insufficient evidence for broad real-world precision. Report the count and coverage honestly and collect a larger independently frozen phone set before shipping calibrated auto-choice.

On the phone, score every row with `JevRuntime.scores`, using the exact model hash and options in manifest order. Export only row ID, model hash and raw score vector; no owner's contacts, recordings or private conversation belongs in the fixture report. Feed these scores to `training/decision/calibrate.py`. Never fit on final rows. Constant-choice mutations must fail before claiming the evaluation is useful.

`apply_edit.py`, `st_numpy.py` and `edit-p50c/` come from https://github.com/RikiyaT/lm_understand-logs
(experiment 8, run p50c), by Takehi, Teoh, Zhang and Khattab, licensed as stated in that repository
(data under CC BY 4.0). They rebuild the edited Qwen3.5-0.8B evaluated in "Weight-Space Programming".
This folder is an owner-only experiment; nothing here ships in the app.

`merge/` (round 6) merges these Gemma 4 E2B fine-tunes for the owner's personal use, with credit:
- google/gemma-4-E2B-it (Google, Gemma terms / Apache 2.0) - the base.
- RinggAI/ringg-router-e2b (Ringg AI, Apache 2.0) - routing, function selection, code-mixed voice-agent decisions.
- yemeni-ai-lab/gemma-4-e2b-yemeni-arabic-assistant-merged (Loay Amin, Yemeni AI Lab, CC BY-NC-SA 4.0) - Arabic dialect assistant.
- ermiaazarkhalili/Gemma4-E2B-Function-Calling-xLAM-Unsloth (Apache 2.0, trained on Salesforce xLAM-60k) - function calling.
Methods: task arithmetic (Ilharco et al. 2023), TIES-Merging (Yadav et al. 2023), DARE (Yu et al. 2024).
`holdout3_exam.json` was written by a separate agent that never saw the guard, the prompts or any model output.

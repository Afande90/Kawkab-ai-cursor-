"""No model outputs are fabricated: these test the report math and mutation gates only."""
import importlib.util
import json
import math
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('decision_calibration', ROOT / 'training/decision/calibrate.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class DecisionCalibrationTest(unittest.TestCase):
    def test_nonfinite_scores_cannot_be_confidence(self):
        with self.assertRaises(ValueError):
            module.probability([math.nan, 1], 1.0)

    def test_constant_option_mutations_fail_final_precision(self):
        rows = [json.loads(line) for line in (ROOT / 'evals/decisions/v1/manifest.jsonl').read_text().splitlines()]
        final = [row for row in rows if row['split'] == 'test']
        for chosen in (0, 1):
            scores = {row['id']: [20 if i == chosen else 0 for i in range(len(row['options']))] for row in final}
            result = module.measurements(final, scores, 1.0, 0.95)
            self.assertLess(result['precision'], 0.95)

    def test_uniform_uncertainty_has_no_auto_actions(self):
        row = dict(id='one', options=['act', 'ask'], label='act')
        self.assertEqual(0, module.measurements([row], {'one': [0, 0]}, 1.0, 0.95)['acted'])

if __name__ == '__main__':
    unittest.main()

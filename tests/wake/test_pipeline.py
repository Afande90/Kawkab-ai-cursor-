"""Mutation and input-integrity tests; no model or target threshold is changed by these tests."""
import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).parents[2] / "training/wake/pipeline.py"
spec = importlib.util.spec_from_file_location("wake_pipeline", path)
pipeline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pipeline)


class WakePipelineTest(unittest.TestCase):
    def test_constant_no_fails_recall(self):
        self.assertFalse(pipeline.gates(0, 534, 0, 10.79)["passed"])

    def test_constant_yes_fails_false_wakes(self):
        self.assertFalse(pipeline.gates(534, 534, 1000, 10.79)["passed"])

    def test_too_little_negative_audio_cannot_pass(self):
        self.assertFalse(pipeline.gates(534, 534, 0, 9.99)["passed"])

    def test_boundary_is_fixed(self):
        self.assertTrue(pipeline.gates(90, 100, 1, 10)["passed"])
        self.assertFalse(pipeline.gates(89, 100, 0, 10)["passed"])

    def test_silence_is_finite(self):
        import numpy as np
        self.assertTrue(np.isfinite(pipeline.features(np.zeros(16000))).all())

    def test_test_voices_are_not_training_voices(self):
        with self.assertRaises(ValueError):
            pipeline.check_disjoint([{"voice": "same", "speaker": 1}], [{"voice": "same", "speaker": 1}])


if __name__ == "__main__":
    unittest.main()

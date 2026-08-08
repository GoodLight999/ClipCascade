import unittest

from connection.retry import RetryPolicy


class RetryPolicyTests(unittest.TestCase):
    def test_first_attempt_stays_inside_jitter_window(self) -> None:
        policy = RetryPolicy()
        self.assertEqual(policy.delay_for_attempt(1, 0.0), 0.5)
        self.assertEqual(policy.delay_for_attempt(1, 1.0), 1.0)

    def test_exponential_delay_is_capped(self) -> None:
        policy = RetryPolicy()
        self.assertEqual(policy.delay_for_attempt(2, 1.0), 2.0)
        self.assertEqual(policy.delay_for_attempt(3, 1.0), 4.0)
        self.assertEqual(policy.delay_for_attempt(10, 1.0), 30.0)
        self.assertEqual(policy.delay_for_attempt(10, 0.0), 15.0)

    def test_custom_jitter_ratio_is_applied(self) -> None:
        policy = RetryPolicy(minimum_jitter_ratio=0.25)
        self.assertEqual(policy.delay_for_attempt(3, 0.0), 1.0)
        self.assertEqual(policy.delay_for_attempt(3, 1.0), 4.0)

    def test_invalid_policy_values_are_rejected(self) -> None:
        with self.assertRaises(ValueError):
            RetryPolicy(base_delay_seconds=0)
        with self.assertRaises(ValueError):
            RetryPolicy(base_delay_seconds=2, cap_delay_seconds=1)
        with self.assertRaises(ValueError):
            RetryPolicy(minimum_jitter_ratio=-0.1)
        with self.assertRaises(ValueError):
            RetryPolicy(minimum_jitter_ratio=1.1)
        with self.assertRaises(ValueError):
            RetryPolicy(stable_reset_seconds=-1)

    def test_invalid_attempt_and_random_values_are_rejected(self) -> None:
        policy = RetryPolicy()
        with self.assertRaises(ValueError):
            policy.delay_for_attempt(0, 0.5)
        with self.assertRaises(ValueError):
            policy.delay_for_attempt(1, -0.1)
        with self.assertRaises(ValueError):
            policy.delay_for_attempt(1, 1.1)


if __name__ == "__main__":
    unittest.main()

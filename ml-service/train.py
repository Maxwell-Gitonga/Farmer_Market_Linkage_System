"""Train (or retrain) the models from the bundled simulated dataset and write the evaluation report.

Usage:  python train.py
"""
import logging

from app.store import STORE

logging.basicConfig(level=logging.INFO, format="%(message)s")

if __name__ == "__main__":
    prices, txns = STORE.load_bundled_data()
    m = STORE.train(prices, txns, source="bundled_simulated_dataset")
    pm, dm = m["price_model"], m["demand_model"]
    print(f"\nPrice model: {pm['selected_model']}  MAPE={100 * pm['comparison'][pm['selected_model']]['mape']:.2f}%  "
          f"(naive {100 * pm['comparison']['naive_last_price_baseline']['mape']:.2f}%)")
    print(f"Demand model: mean MAPE={100 * dm['mean_mape']:.2f}%  (naive {100 * dm['mean_naive_mape']:.2f}%)")
    print("Report written to reports/model_evaluation.md")

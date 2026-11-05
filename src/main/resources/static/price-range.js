/*
 * price-range.js: a min/max price filter: two draggable handles on one track plus Min/Max boxes for exact values.
 * The handles and boxes stay in sync.
 *
 *   const range = PriceRange.create(containerElement, { max: 1000, step: 5, onChange: ({min, max}) => ... });
 *   range.get();   // { min, max }
 */
(function () {
  'use strict';

  const STYLE_ID = 'price-range-style';

  function injectStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement('style');
    style.id = STYLE_ID;
    // Two range inputs are stacked; only their thumbs take pointer events, so both handles can be dragged.
    style.textContent = `
      .pr-track { position: relative; height: 28px; }
      .pr-track .pr-bar { position: absolute; left: 0; right: 0; top: 11px; height: 6px; border-radius: 3px; background: #e5e0d8; }
      .pr-track .pr-fill { position: absolute; top: 11px; height: 6px; border-radius: 3px; background: #c17c5f; }
      .pr-track input[type=range] {
        position: absolute; left: 0; top: 0; width: 100%; height: 28px; margin: 0;
        background: transparent; pointer-events: none; -webkit-appearance: none; appearance: none;
      }
      .pr-track input[type=range]::-webkit-slider-runnable-track { background: transparent; height: 28px; }
      .pr-track input[type=range]::-moz-range-track { background: transparent; height: 28px; }
      .pr-track input[type=range]::-webkit-slider-thumb {
        -webkit-appearance: none; appearance: none; pointer-events: auto; cursor: grab;
        width: 20px; height: 20px; margin-top: 4px; border-radius: 50%;
        background: #c17c5f; border: 2px solid #fff; box-shadow: 0 1px 3px rgba(0,0,0,.3);
      }
      .pr-track input[type=range]::-moz-range-thumb {
        pointer-events: auto; cursor: grab; width: 18px; height: 18px; border-radius: 50%;
        background: #c17c5f; border: 2px solid #fff; box-shadow: 0 1px 3px rgba(0,0,0,.3);
      }
      .pr-track input[type=range]:active::-webkit-slider-thumb { cursor: grabbing; background: #a5664d; }
      .pr-boxes { display: flex; gap: 8px; margin-top: 10px; }
      .pr-boxes label { flex: 1; font-size: 12px; color: #8b7355; }
      .pr-boxes input {
        width: 100%; margin-top: 2px; padding: 6px 8px; border: 1px solid #e5e0d8; border-radius: 8px;
        color: #5c4a3d; font-size: 14px; background: #fff;
      }
      .pr-boxes input:focus { outline: none; border-color: #c17c5f; }
    `;
    document.head.appendChild(style);
  }

  function create(container, options) {
    injectStyle();
    const max = Math.max(1, Math.ceil(options.max || 1000));
    const step = options.step || 1;
    container.innerHTML = `
      <div class="pr-track">
        <div class="pr-bar"></div><div class="pr-fill"></div>
        <input type="range" min="0" max="${max}" step="${step}" value="0" aria-label="Minimum price">
        <input type="range" min="0" max="${max}" step="${step}" value="${max}" aria-label="Maximum price">
      </div>
      <div class="pr-boxes">
        <label>Min (BD)<input type="number" min="0" max="${max}" step="any" value="0" inputmode="decimal"></label>
        <label>Max (BD)<input type="number" min="0" max="${max}" step="any" value="${max}" inputmode="decimal"></label>
      </div>`;
    const [lowSlider, highSlider] = container.querySelectorAll('input[type=range]');
    const [lowBox, highBox] = container.querySelectorAll('input[type=number]');
    const fill = container.querySelector('.pr-fill');

    let low = 0;
    let high = max;

    function render() {
      lowSlider.value = Math.min(low, max);
      highSlider.value = Math.min(high, max);
      fill.style.left = (Math.min(low, max) / max * 100) + '%';
      fill.style.right = (100 - Math.min(high, max) / max * 100) + '%';
      // When both handles meet at the right end, keep the low handle on top so it can be dragged back.
      lowSlider.style.zIndex = low >= max - step ? '3' : '2';
      highSlider.style.zIndex = '2';
    }

    function changed() {
      render();
      if (options.onChange) options.onChange({ min: low, max: high });
    }

    lowSlider.addEventListener('input', () => {
      low = Math.min(Number(lowSlider.value), high);
      lowBox.value = low;
      changed();
    });
    highSlider.addEventListener('input', () => {
      high = Math.max(Number(highSlider.value), low);
      highBox.value = high;
      changed();
    });

    function fromBoxes() {
      let a = parseFloat(lowBox.value);
      let b = parseFloat(highBox.value);
      if (isNaN(a) || a < 0) a = 0;
      if (isNaN(b)) b = max;
      if (a > b) [a, b] = [b, a];
      low = a;
      high = b;
      lowBox.value = low;
      highBox.value = high;
      changed();
    }
    lowBox.addEventListener('change', fromBoxes);
    highBox.addEventListener('change', fromBoxes);
    [lowBox, highBox].forEach(box => box.addEventListener('keydown', e => {
      if (e.key === 'Enter') {
        e.preventDefault();
        fromBoxes();
      }
    }));

    render();
    return {
      get: () => ({ min: low, max: high })
    };
  }

  window.PriceRange = { create: create };
})();

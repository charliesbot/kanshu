(function() {
  const bridge = window.kanshuBridge;
  const loadId = window.__kanshuChapterLoadId__;
  let activeSettingsRevision = 0;
  let scrollTimeout = null;

  window.kanshu = {
    scrollToPage: function(pageIndex) {
      const viewportWidth = window.innerWidth;
      const targetScrollX = pageIndex * viewportWidth;
      window.scrollTo(targetScrollX, 0);
      this.reportPageSettled();
    },

    reportPageSettled: function() {
      if (!bridge) return;
      const scrollX = window.scrollX;
      const scrollWidth = document.documentElement.scrollWidth || document.body.scrollWidth;
      const viewportWidth = window.innerWidth;

      const pageIndex = Math.round(scrollX / viewportWidth);
      const maxScrollX = Math.max(0, scrollWidth - viewportWidth);
      const progress = maxScrollX === 0 ? 0.0 : Math.min(1.0, Math.max(0.0, scrollX / maxScrollX));

      bridge.onPageSettled(loadId, pageIndex, progress);
    },

    repaginate: function(revision, restoredPageIndex = 0) {
      if (!bridge) return;
      
      const start = Date.now();
      const checkStable = () => {
        let lastWidth = -1;
        let sameCount = 0;

        const checkFrame = () => {
          const currentWidth = document.documentElement.scrollWidth || document.body.scrollWidth;
          if (currentWidth === lastWidth && currentWidth > 0) {
            sameCount++;
          } else {
            sameCount = 0;
            lastWidth = currentWidth;
          }

          const elapsed = Date.now() - start;
          if (sameCount >= 2) {
            // Layout is stable
            const viewportWidth = window.innerWidth;
            const pageCount = Math.max(1, Math.round(currentWidth / viewportWidth));
            const clampedRestored = Math.min(pageCount - 1, Math.max(0, restoredPageIndex));
            
            // Snap scroll to correct position
            window.scrollTo(clampedRestored * viewportWidth, 0);
            
            bridge.onRepaginated(loadId, revision, pageCount, clampedRestored, false);
          } else if (elapsed > 2000) {
            // Timeout reached, fallback
            const viewportWidth = window.innerWidth;
            const pageCount = Math.max(1, Math.round(currentWidth / viewportWidth));
            bridge.onRepaginated(loadId, revision, pageCount, restoredPageIndex, true);
          } else {
            requestAnimationFrame(checkFrame);
          }
        };

        requestAnimationFrame(checkFrame);
      };

      // Race font loading with a 500ms timeout before starting layout checks
      let fontTimeout = setTimeout(checkStable, 500);
      if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(() => {
          clearTimeout(fontTimeout);
          checkStable();
        }).catch(() => {
          clearTimeout(fontTimeout);
          checkStable();
        });
      } else {
        clearTimeout(fontTimeout);
        checkStable();
      }
    },

    applySettings: function(settingsJson, revision) {
      activeSettingsRevision = revision;
      
      // 1. Capture current progress inside spine before relayout
      const scrollX = window.scrollX;
      const scrollWidth = document.documentElement.scrollWidth || document.body.scrollWidth;
      const viewportWidth = window.innerWidth;
      const maxScrollX = Math.max(0, scrollWidth - viewportWidth);
      const progressInSpine = maxScrollX === 0 ? 0.0 : scrollX / maxScrollX;

      // 2. Apply typography CSS variables onto root document
      const settings = JSON.parse(settingsJson);
      const root = document.documentElement;
      
      if (settings.font) {
        root.style.setProperty('--reader-font', settings.font);
      }
      if (settings.fontSize) {
        root.style.setProperty('--font-size', settings.fontSize);
      }
      if (settings.lineHeight) {
        root.style.setProperty('--line-height', settings.lineHeight.toString());
      }
      if (settings.alignment) {
        root.style.setProperty('--text-align', settings.alignment);
      }
      if (settings.marginInline) {
        root.style.setProperty('--page-margin-inline', settings.marginInline);
      }
      if (settings.marginBlock) {
        root.style.setProperty('--page-margin-block', settings.marginBlock);
      }
      if (settings.paragraphSpacing !== undefined) {
        root.style.setProperty('--paragraph-spacing', settings.paragraphSpacing + 'em');
      }
      if (settings.wordSpacing !== undefined) {
        root.style.setProperty('--word-spacing', settings.wordSpacing + 'em');
      }
      if (settings.letterSpacing !== undefined) {
        root.style.setProperty('--letter-spacing', settings.letterSpacing + 'em');
      }

      // 3. Settling process to restore progress
      const start = Date.now();
      const checkSettled = () => {
        let lastWidth = -1;
        let sameCount = 0;

        const checkFrame = () => {
          // Verify we haven't been interrupted by a newer settings change
          if (revision !== activeSettingsRevision) return;

          const currentWidth = document.documentElement.scrollWidth || document.body.scrollWidth;
          if (currentWidth === lastWidth && currentWidth > 0) {
            sameCount++;
          } else {
            sameCount = 0;
            lastWidth = currentWidth;
          }

          const elapsed = Date.now() - start;
          if (sameCount >= 2) {
            const newViewportWidth = window.innerWidth;
            const newMaxScrollX = Math.max(0, currentWidth - newViewportWidth);
            const targetScrollX = progressInSpine * newMaxScrollX;
            const newPageIndex = Math.round(targetScrollX / newViewportWidth);
            
            // Snap scroll to target
            window.scrollTo(newPageIndex * newViewportWidth, 0);
            
            const pageCount = Math.max(1, Math.round(currentWidth / newViewportWidth));
            bridge.onRepaginated(loadId, revision, pageCount, newPageIndex, false);
          } else if (elapsed > 2000) {
            const newViewportWidth = window.innerWidth;
            const pageCount = Math.max(1, Math.round(currentWidth / newViewportWidth));
            bridge.onRepaginated(loadId, revision, pageCount, 0, true);
          } else {
            requestAnimationFrame(checkFrame);
          }
        };

        requestAnimationFrame(checkFrame);
      };

      // Race font loading with a 500ms timeout
      let fontTimeout = setTimeout(checkSettled, 500);
      if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(() => {
          clearTimeout(fontTimeout);
          checkSettled();
        }).catch(() => {
          clearTimeout(fontTimeout);
          checkSettled();
        });
      } else {
        clearTimeout(fontTimeout);
        checkSettled();
      }
    }
  };

  // Debounced scroll listener to report natural scroll updates (e.g. hash changes or anchors)
  window.addEventListener('scroll', () => {
    if (scrollTimeout) {
      clearTimeout(scrollTimeout);
    }
    scrollTimeout = setTimeout(() => {
      window.kanshu.reportPageSettled();
    }, 50);
  });

  // Handle local anchor fragment transitions
  window.addEventListener('hashchange', () => {
    window.kanshu.reportPageSettled();
  });
})();

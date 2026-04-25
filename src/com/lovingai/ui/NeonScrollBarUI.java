package com.lovingai.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

public final class NeonScrollBarUI extends BasicScrollBarUI {

    @Override
    protected void configureScrollBarColors() {
        trackColor = new Color(0x031018);
        thumbColor = new Color(0x219bb3);
        thumbDarkShadowColor = new Color(0x07202b);
        thumbHighlightColor = new Color(0x63dff2);
        thumbLightShadowColor = new Color(0x07202b);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return createZeroButton();
    }

    private static JButton createZeroButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        b.setOpaque(false);
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle trackBounds) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.18f));
        g2.setColor(trackColor);
        int arc = Math.min(trackBounds.width, trackBounds.height);
        RoundRectangle2D rr =
                new RoundRectangle2D.Float(
                        trackBounds.x + 1,
                        trackBounds.y + 1,
                        Math.max(0, trackBounds.width - 2),
                        Math.max(0, trackBounds.height - 2),
                        arc,
                        arc);
        g2.fill(rr);
        g2.dispose();
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int arc = Math.min(thumbBounds.width, thumbBounds.height);
        float x = thumbBounds.x + 2;
        float y = thumbBounds.y + 2;
        float w = Math.max(0, thumbBounds.width - 4);
        float h = Math.max(0, thumbBounds.height - 4);
        RoundRectangle2D rr = new RoundRectangle2D.Float(x, y, w, h, arc, arc);

        g2.setComposite(AlphaComposite.SrcOver.derive(0.90f));
        g2.setColor(thumbColor);
        g2.fill(rr);

        g2.setComposite(AlphaComposite.SrcOver.derive(0.25f));
        g2.setColor(thumbHighlightColor);
        g2.draw(rr);

        g2.setComposite(AlphaComposite.SrcOver.derive(0.12f));
        g2.setColor(new Color(0x00f0ff));
        RoundRectangle2D glow = new RoundRectangle2D.Float(x - 1, y - 1, w + 2, h + 2, arc, arc);
        g2.draw(glow);

        g2.dispose();
    }
}

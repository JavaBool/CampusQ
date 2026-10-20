
(() => {
    'use strict';

    const CART_KEY = 'campusbite-cart';

    const money = value =>
        '₹' + Number(value).toLocaleString('en-IN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });

    // --------------------------------------------------
    // CART STORAGE
    // --------------------------------------------------

    const readCart = () => {
        try {
            const parsed = JSON.parse(
                localStorage.getItem(CART_KEY) || '{}'
            );

            // Support both the object and legacy array formats.
            const entries = Array.isArray(parsed)
                ? parsed
                : Object.values(parsed || {});

            const cart = {};

            entries.forEach(item => {
                if (
                    !item ||
                    item.id == null ||
                    String(item.id).trim() === ''
                ) {
                    return;
                }

                const id = String(item.id);
                const quantity = Number(item.quantity);
                const price = Number(item.price);

                if (
                    !Number.isSafeInteger(quantity) ||
                    quantity <= 0 ||
                    !Number.isFinite(price) ||
                    price < 0
                ) {
                    return;
                }

                if (cart[id]) {
                    cart[id].quantity += quantity;
                } else {
                    cart[id] = {
                        id,
                        title: String(item.title || ''),
                        price,
                        image: String(item.image || ''),
                        quantity
                    };
                }
            });

            return cart;
        } catch (error) {
            console.error('Unable to read the cart:', error);
            return {};
        }
    };

    const updateCartCount = cart => {
        const count = Object.values(cart).reduce(
            (sum, item) => sum + item.quantity,
            0
        );

        document.querySelectorAll('#navCartCount').forEach(element => {
            element.textContent = count;
        });
    };

    const saveCart = cart => {
        try {
            localStorage.setItem(CART_KEY, JSON.stringify(cart));
            updateCartCount(cart);
            return true;
        } catch (error) {
            console.error('Unable to save the cart:', error);
            toast('Unable to save your cart. Check your browser storage.');
            return false;
        }
    };

    const clearCart = () => {
        try {
            localStorage.removeItem(CART_KEY);
            updateCartCount({});
            return true;
        } catch (error) {
            console.error('Unable to clear the cart:', error);
            toast('Unable to clear your cart.');
            return false;
        }
    };

    // --------------------------------------------------
    // TOAST MESSAGES
    // --------------------------------------------------

    const toast = message => {
        const element = document.getElementById('toastMessage');
        if (!element) return;

        element.textContent = message;
        element.classList.add('show');

        clearTimeout(toast.timer);

        toast.timer = setTimeout(() => {
            element.classList.remove('show');
        }, 2100);
    };

    // Initial page setup.
    updateCartCount(readCart());

    document.querySelectorAll('#year').forEach(element => {
        element.textContent = new Date().getFullYear();
    });

    // --------------------------------------------------
    // MENU: ADD ITEMS TO CART
    // --------------------------------------------------

    document.querySelectorAll('[data-add]').forEach(button => {
        button.addEventListener('click', () => {
            if (button.disabled) return;

            const card = button.closest('.food-card');
            if (!card) return;

            // Verify that the button and card reference the same DB item.
            const buttonId = button.dataset.id;
            const cardId = card.dataset.id;

            if (
                !buttonId ||
                !cardId ||
                String(buttonId) !== String(cardId)
            ) {
                console.error('Missing or mismatched database item ID.');
                toast('Unable to add this item. Please refresh the menu.');
                return;
            }

            const id = String(buttonId);
            const title = card.dataset.title;
            const price = Number(card.dataset.price);
            const image = card.dataset.image || '';

            if (
                !title ||
                !Number.isFinite(price) ||
                price < 0
            ) {
                console.error('Invalid menu item data:', {
                    id,
                    title,
                    price
                });

                toast('Unable to add this item.');
                return;
            }

            const cart = readCart();

            if (cart[id]) {
                cart[id].quantity += 1;
            } else {
                cart[id] = {
                    id,
                    title,
                    price,
                    image,
                    quantity: 1
                };
            }

            if (!saveCart(cart)) return;

            const originalHTML = button.innerHTML;
            button.innerHTML = 'Added <span>✓</span>';

            toast(title + ' added to your cart');

            clearTimeout(button.resetTimer);

            button.resetTimer = setTimeout(() => {
                if (button.isConnected) {
                    button.innerHTML = originalHTML;
                }
            }, 1000);
        });
    });

    // --------------------------------------------------
    // MENU: FAVOURITES
    // --------------------------------------------------

    document.querySelectorAll('.heart-button').forEach(button => {
        button.addEventListener('click', () => {
            button.classList.toggle('liked');

            button.textContent = button.classList.contains('liked')
                ? '♥'
                : '♡';
        });
    });

    // --------------------------------------------------
    // MENU: SEARCH AND CATEGORY FILTERS
    // --------------------------------------------------

    const search = document.getElementById('menuSearch');

    const categoryButtons = [
        ...document.querySelectorAll('[data-category]')
    ];

    const cards = [
        ...document.querySelectorAll('.food-card')
    ];

    let activeCategory = 'all';

    const filterCards = () => {
        const term = (search?.value || '').trim().toLowerCase();
        let visible = 0;

        cards.forEach(card => {
            const searchableText = (
                (card.dataset.name || '') + ' ' +
                (card.dataset.title || '')
            ).toLowerCase();

            const matchesText = searchableText.includes(term);

            const matchesCategory =
                activeCategory === 'all' ||
                (card.dataset.category || '') === activeCategory;

            const show = matchesText && matchesCategory;

            card.classList.toggle('d-none', !show);

            if (show) visible++;
        });

        const resultCount = document.getElementById('resultCount');
        const empty = document.getElementById('emptySearch');

        if (resultCount) {
            resultCount.textContent = visible;
        }

        if (empty) {
            empty.classList.toggle('d-none', visible !== 0);
        }
    };

    if (search) {
        search.addEventListener('input', filterCards);
    }

    categoryButtons.forEach(button => {
        button.addEventListener('click', () => {
            activeCategory = button.dataset.category || 'all';

            categoryButtons.forEach(item => {
                item.classList.toggle('active', item === button);
            });

            filterCards();
        });
    });

    // --------------------------------------------------
    // CART PAGE
    // --------------------------------------------------

    const cartItems = document.getElementById('cartItems');

    if (cartItems) {
        const empty = document.getElementById('cartEmpty');
        const itemCount = document.getElementById('cartItemCount');
        const subtotal = document.getElementById('cartSubtotal');
        const total = document.getElementById('cartTotal');
        const checkout = document.getElementById('checkoutButton');

        const renderCart = () => {
            const current = readCart();
            const entries = Object.values(current);

            const count = entries.reduce(
                (sum, item) => sum + item.quantity,
                0
            );

            const amount = entries.reduce(
                (sum, item) => sum + item.price * item.quantity,
                0
            );

            if (itemCount) itemCount.textContent = count;
            if (subtotal) subtotal.textContent = money(amount);
            if (total) total.textContent = money(amount);

            if (checkout) {
                checkout.disabled = count === 0;
            }

            if (empty) {
                empty.classList.toggle('d-none', count !== 0);
            }

            cartItems.innerHTML = '';

            entries.forEach(item => {
                const row = document.createElement('article');
                row.className = 'cart-item';

                const image = document.createElement('img');
                image.className = 'cart-item-image';
                image.alt = item.title;

                if (item.image) {
                    image.src = item.image;
                } else {
                    image.classList.add('d-none');
                }

                const main = document.createElement('div');
                main.className = 'cart-item-main';

                const title = document.createElement('div');
                title.className = 'cart-item-title';
                title.textContent = item.title;

                const description = document.createElement('div');
                description.className = 'cart-item-description';
                description.textContent =
                    'Prepared fresh at the campus canteen';

                const quantityControls = document.createElement('div');
                quantityControls.className = 'quantity-control';

                const minus = document.createElement('button');
                minus.type = 'button';
                minus.setAttribute('aria-label', 'Decrease quantity');
                minus.textContent = '−';

                const quantity = document.createElement('span');
                quantity.textContent = item.quantity;

                const plus = document.createElement('button');
                plus.type = 'button';
                plus.setAttribute('aria-label', 'Increase quantity');
                plus.textContent = '+';

                minus.addEventListener('click', () => {
                    changeQuantity(item.id, -1);
                });

                plus.addEventListener('click', () => {
                    changeQuantity(item.id, 1);
                });

                quantityControls.append(minus, quantity, plus);
                main.append(title, description, quantityControls);

                const price = document.createElement('div');
                price.className = 'cart-item-price';

                const lineTotal = document.createElement('span');
                lineTotal.textContent = money(
                    item.price * item.quantity
                );

                const remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'remove-item';
                remove.textContent = 'Remove';

                remove.addEventListener('click', () => {
                    const next = readCart();

                    delete next[String(item.id)];

                    if (saveCart(next)) {
                        renderCart();
                        toast(item.title + ' removed from your cart');
                    }
                });

                price.append(lineTotal, remove);
                row.append(image, main, price);
                cartItems.append(row);
            });

            updateCartCount(current);
        };

        const changeQuantity = (id, delta) => {
            const next = readCart();
            const key = String(id);

            if (!next[key]) return;

            next[key].quantity += delta;

            if (next[key].quantity <= 0) {
                delete next[key];
            }

            if (saveCart(next)) {
                renderCart();
            }
        };

        // --------------------------------------------------
        // CHECKOUT
        // --------------------------------------------------

        checkout?.addEventListener('click', event => {
            event.preventDefault();

            const current = readCart();
            const entries = Object.values(current);

            if (entries.length === 0) {
                toast('Your cart is empty.');
                return;
            }

            // Build the complete checkout request BEFORE clearing the cart.
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/order/checkout';

            // Include the CSRF token when Spring Security provides one.
            const csrfToken = document.querySelector(
                'meta[name="_csrf"]'
            )?.content;

            const csrfParameter = document.querySelector(
                'meta[name="_csrf_parameter"]'
            )?.content;

            if (csrfToken && csrfParameter) {
                const csrfInput = document.createElement('input');
                csrfInput.type = 'hidden';
                csrfInput.name = csrfParameter;
                csrfInput.value = csrfToken;
                form.appendChild(csrfInput);
            }

            entries.forEach((item, index) => {
                const idInput = document.createElement('input');
                idInput.type = 'hidden';
                idInput.name = `items[${index}].itemId`;
                idInput.value = String(item.id);

                const quantityInput = document.createElement('input');
                quantityInput.type = 'hidden';
                quantityInput.name = `items[${index}].quantity`;
                quantityInput.value = String(item.quantity);

                form.append(idInput, quantityInput);
            });

            document.body.appendChild(form);

            renderCart();

            // Submit the preserved item data to Spring Boot.
            form.submit();
        });

        renderCart();
    }
})();

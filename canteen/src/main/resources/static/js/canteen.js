(() => {
    const CART_KEY = 'campusbite-cart';
    const money = value => '₹' + Number(value).toLocaleString('en-IN');
    const readCart = () => {
        try { return JSON.parse(localStorage.getItem(CART_KEY) || '{}'); }
        catch (_) { return {}; }
    };
    const saveCart = cart => {
        localStorage.setItem(CART_KEY, JSON.stringify(cart));
        updateCartCount(cart);
    };
    const updateCartCount = cart => {
        const count = Object.values(cart).reduce((sum, item) => sum + item.quantity, 0);
        document.querySelectorAll('#navCartCount').forEach(el => el.textContent = count);
    };
    const toast = message => {
        const el = document.getElementById('toastMessage');
        if (!el) return;
        el.textContent = message;
        el.classList.add('show');
        clearTimeout(toast.timer);
        toast.timer = setTimeout(() => el.classList.remove('show'), 2100);
    };
    const cart = readCart();
    updateCartCount(cart);
    document.querySelectorAll('#year').forEach(el => el.textContent = new Date().getFullYear());

    document.querySelectorAll('[data-add]').forEach(button => {
        button.addEventListener('click', () => {
            const card = button.closest('.food-card');
            const id = card.dataset.id;
            const existing = readCart();
            if (existing[id]) existing[id].quantity += 1;
            else existing[id] = {
                id, title: card.dataset.title, price: Number(card.dataset.price),
                image: card.dataset.image, quantity: 1
            };
            saveCart(existing);
            button.innerHTML = 'Added <span>✓</span>';
            toast(card.dataset.title + ' added to your cart');
            setTimeout(() => { button.innerHTML = 'Add <span>+</span>'; }, 1000);
        });
    });

    document.querySelectorAll('.heart-button').forEach(button => {
        button.addEventListener('click', () => {
            button.classList.toggle('liked');
            button.textContent = button.classList.contains('liked') ? '♥' : '♡';
        });
    });

    const search = document.getElementById('menuSearch');
    const categoryButtons = [...document.querySelectorAll('[data-category]')];
    const cards = [...document.querySelectorAll('.food-card')];
    let activeCategory = 'all';
    const filterCards = () => {
        const term = (search?.value || '').trim().toLowerCase();
        let visible = 0;
        cards.forEach(card => {
            const matchesText = (card.dataset.name + ' ' + card.dataset.title).toLowerCase().includes(term);
            const matchesCategory = activeCategory === 'all' || card.dataset.category === activeCategory;
            const show = matchesText && matchesCategory;
            card.classList.toggle('d-none', !show);
            if (show) visible++;
        });
        const resultCount = document.getElementById('resultCount');
        const empty = document.getElementById('emptySearch');
        if (resultCount) resultCount.textContent = visible;
        if (empty) empty.classList.toggle('d-none', visible !== 0);
    };
    if (search) search.addEventListener('input', filterCards);
    categoryButtons.forEach(button => button.addEventListener('click', () => {
        activeCategory = button.dataset.category;
        categoryButtons.forEach(item => item.classList.toggle('active', item === button));
        filterCards();
    }));

    const cartItems = document.getElementById('cartItems');
    if (cartItems) {
        const empty = document.getElementById('cartEmpty');
        const itemCount = document.getElementById('cartItemCount');
        const subtotal = document.getElementById('cartSubtotal');
        const total = document.getElementById('cartTotal');
        const checkout = document.getElementById('checkoutButton');
        const notice = document.getElementById('checkoutNotice');

        const renderCart = () => {
            const current = readCart();
            const entries = Object.values(current);
            const count = entries.reduce((sum, item) => sum + item.quantity, 0);
            const amount = entries.reduce((sum, item) => sum + item.price * item.quantity, 0);
            itemCount.textContent = count;
            subtotal.textContent = money(amount);
            total.textContent = money(amount);
            checkout.disabled = count === 0;
            empty.classList.toggle('d-none', count !== 0);
            cartItems.innerHTML = '';
            entries.forEach(item => {
                const row = document.createElement('article');
                row.className = 'cart-item';
                const image = document.createElement('img');
                image.className = 'cart-item-image';
                image.src = item.image;
                image.alt = item.title;
                const main = document.createElement('div');
                main.className = 'cart-item-main';
                const title = document.createElement('div');
                title.className = 'cart-item-title';
                title.textContent = item.title;
                const desc = document.createElement('div');
                desc.className = 'cart-item-description';
                desc.textContent = 'Prepared fresh at the campus canteen';
                const qty = document.createElement('div');
                qty.className = 'quantity-control';
                const minus = document.createElement('button');
                minus.type = 'button'; minus.setAttribute('aria-label', 'Decrease quantity'); minus.textContent = '−';
                const number = document.createElement('span'); number.textContent = item.quantity;
                const plus = document.createElement('button');
                plus.type = 'button'; plus.setAttribute('aria-label', 'Increase quantity'); plus.textContent = '+';
                minus.addEventListener('click', () => changeQuantity(item.id, -1));
                plus.addEventListener('click', () => changeQuantity(item.id, 1));
                qty.append(minus, number, plus);
                main.append(title, desc, qty);
                const price = document.createElement('div');
                price.className = 'cart-item-price';
                const lineTotal = document.createElement('span'); lineTotal.textContent = money(item.price * item.quantity);
                const remove = document.createElement('button');
                remove.type = 'button'; remove.className = 'remove-item'; remove.textContent = 'Remove';
                remove.addEventListener('click', () => {
                    const next = readCart(); delete next[item.id]; saveCart(next); renderCart();
                });
                price.append(lineTotal, remove);
                row.append(image, main, price);
                cartItems.append(row);
            });
            updateCartCount(current);
        };
        const changeQuantity = (id, delta) => {
            const next = readCart();
            if (!next[id]) return;
            next[id].quantity += delta;
            if (next[id].quantity <= 0) delete next[id];
            saveCart(next);
            renderCart();
        };
        checkout?.addEventListener('click', () => {
            notice?.classList.remove('d-none');
            notice?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        });
        renderCart();
    }
})();